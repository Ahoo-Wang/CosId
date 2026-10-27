/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.ahoo.cosid.store.mongo;

import me.ahoo.cosid.NamespaceMismatchException;
import me.ahoo.cosid.lease.AcquireReason;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.lease.SlotLeaseStore;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoServerException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link SlotLeaseStore} on the {@code cosid_slot} collection (06-stores.md §2.3): one document per slot,
 * {@code {ns, slot, ownerKey, incarnation, lastOwnerKey, fence, expiresAt, floor, ceiling, nextFloor, writtenAt}}, unique
 * on {@code (ns, slot)}, with every slot of a namespace created on its first use; the namespace parameters live in
 * {@code cosid_namespace}, {@code {_id: ns, limit, toleranceMs}}.
 *
 * <p>An acquire first reads the slots the owner key holds or held last (one aggregation, with {@code $$NOW}): that
 * answers the idempotent step and the duplicate key check and names the sticky candidates. Each step that takes a slot
 * (05-machine-lease.md §2.1) is then one {@code findOneAndUpdate} on one document, so the condition and the change of
 * §2.2 are atomic: "free" is a {@code $expr} on {@code $$NOW}, a sticky candidate also matches the fence it was read
 * with, renewals and releases match the incarnation and the fence. The pipeline update first stamps {@code writtenAt = $$NOW}, the store clock, and
 * derives the expiry from it, so {@code storeNow} and {@code expiresAt} come from the same write.
 */
public final class MongoSlotLeaseStore implements SlotLeaseStore {
    /** The slot collection. */
    public static final String COLLECTION = "cosid_slot";
    /** The namespace collection. */
    public static final String NAMESPACE_COLLECTION = "cosid_namespace";
    static final String NS = "ns";
    static final String SLOT = "slot";
    static final String OWNER_KEY = "ownerKey";
    static final String INCARNATION = "incarnation";
    static final String LAST_OWNER_KEY = "lastOwnerKey";
    static final String FENCE = "fence";
    static final String EXPIRES_AT = "expiresAt";
    static final String FLOOR = "floor";
    static final String CEILING = "ceiling";
    static final String NEXT_FLOOR = "nextFloor";
    static final String WRITTEN_AT = "writtenAt";
    /** The store clock of the acquire read, a computed field never stored. */
    private static final String READ_AT = "readAt";
    static final String LIMIT = "limit";
    static final String TOLERANCE_MS = "toleranceMs";
    /** The store clock: {@code $$NOW} in epoch milliseconds. */
    private static final Document NOW = new Document("$toLong", "$$NOW");
    /** Free: nobody holds the slot, or the lease expired (§2.1). Released slots keep an explicit null incarnation. */
    private static final Bson FREE = Filters.expr(new Document("$or", List.of(
        new Document("$eq", Arrays.asList("$" + INCARNATION, null)),
        new Document("$lt", List.of("$" + EXPIRES_AT, NOW)))));
    private static final Bson STAMP = new Document("$set", new Document(WRITTEN_AT, NOW));
    /** Attempts of the namespace upsert that lose the first-use race (E11000) before the error is rethrown. */
    private static final int NAMESPACE_ATTEMPTS = 5;
    private static final FindOneAndUpdateOptions AFTER = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
    private static final FindOneAndUpdateOptions LOWEST_AFTER = new FindOneAndUpdateOptions()
        .returnDocument(ReturnDocument.AFTER).sort(Sorts.ascending(SLOT));

    private final MongoCollection<Document> slots;
    private final MongoCollection<Document> namespaces;
    /** Namespaces this instance checked and provisioned; namespaces are never deleted (D23). */
    private final Map<String, NamespaceParams> known = new ConcurrentHashMap<>();
    private volatile boolean indexed;

    /**
     * A slot lease store on a database whose client the caller owns.
     *
     * @param database the database
     * @param timeout  the operation timeout ({@code timeoutMS}); zero means none
     */
    public MongoSlotLeaseStore(MongoDatabase database, Duration timeout) {
        MongoDatabase configured = MongoStore.configure(database, timeout);
        this.slots = configured.getCollection(COLLECTION);
        this.namespaces = configured.getCollection(NAMESPACE_COLLECTION);
    }

    @Override
    public AcquireResult acquire(String namespace, Owner owner, int limit, Duration ttl, Duration tolerance, OptionalInt preferredSlot) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(preferredSlot, "preferredSlot");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive: " + limit);
        }
        final long ttlMillis = positiveMillis(ttl, "ttl");
        long toleranceMillis = toleranceMillis(tolerance);
        ensureNamespace(namespace, new NamespaceParams(limit, toleranceMillis));
        final Bson inNamespace = Filters.and(Filters.eq(NS, namespace), Filters.lt(SLOT, limit));
        String incarnation = owner.incarnation().toString();

        // One read of the slots the key holds or held last, stamped with the store clock: it finds this owner's slot,
        // live leases of other incarnations of the key and the sticky candidates, so the common acquire is one read
        // and one conditional update.
        List<Document> ofKey = slots.aggregate(List.of(
            Aggregates.match(Filters.and(Filters.eq(NS, namespace), Filters.or(Filters.eq(OWNER_KEY, owner.key()), Filters.eq(LAST_OWNER_KEY, owner.key())))),
            Aggregates.addFields(new Field<>(READ_AT, NOW)),
            Aggregates.sort(Sorts.ascending(SLOT)))).into(new ArrayList<>());
        List<Integer> liveOfOtherIncarnations = new ArrayList<>();
        Document held = null;
        List<Document> stickyCandidates = new ArrayList<>();
        for (Document slot : ofKey) {
            long now = slot.getLong(READ_AT);
            boolean free = slot.get(INCARNATION) == null || slot.getLong(EXPIRES_AT) < now;
            boolean below = slot.getInteger(SLOT) < limit;
            if (owner.key().equals(slot.getString(OWNER_KEY))) {
                if (incarnation.equals(slot.getString(INCARNATION))) {
                    held = below && held == null ? slot : held;
                } else if (!free) {
                    // Another incarnation of the key with a live lease (§2.1): only observed, never taken over.
                    liveOfOtherIncarnations.add(slot.getInteger(SLOT));
                }
            }
            if (below && free && owner.key().equals(slot.getString(LAST_OWNER_KEY))) {
                stickyCandidates.add(slot);
            }
        }
        // 1. A slot this very owner holds: returned as it is, stamped with the read.
        if (held != null) {
            return new AcquireResult.Acquired(toLease(held, owner, held.getLong(READ_AT)), AcquireReason.IDEMPOTENT, !liveOfOtherIncarnations.isEmpty());
        }
        List<Bson> take = take(owner, ttlMillis, toleranceMillis);
        // 2. The preferred slot, if free.
        if (preferredSlot.isPresent() && preferredSlot.getAsInt() >= 0 && preferredSlot.getAsInt() < limit) {
            Document preferred = slots.findOneAndUpdate(Filters.and(Filters.eq(NS, namespace), Filters.eq(SLOT, preferredSlot.getAsInt()), FREE),
                take, AFTER);
            if (preferred != null) {
                return acquired(preferred, owner, AcquireReason.PREFERRED, liveOfOtherIncarnations);
            }
        }
        // 3. A free slot the owner key held last, as read: taken if its fence did not move and it is still free.
        for (Document candidate : stickyCandidates) {
            Document sticky = slots.findOneAndUpdate(Filters.and(Filters.eq(NS, namespace), Filters.eq(SLOT, candidate.getInteger(SLOT)),
                Filters.eq(FENCE, candidate.getLong(FENCE)), FREE), take, AFTER);
            if (sticky != null) {
                return acquired(sticky, owner, AcquireReason.STICKY, liveOfOtherIncarnations);
            }
        }
        // 4. The lowest free slot.
        Document lowest = slots.findOneAndUpdate(Filters.and(inNamespace, FREE), take, LOWEST_AFTER);
        if (lowest != null) {
            return acquired(lowest, owner, AcquireReason.LOWEST, liveOfOtherIncarnations);
        }
        // 5. Every slot is held.
        Document earliest = slots.find(inNamespace).sort(Sorts.ascending(EXPIRES_AT)).projection(Projections.include(EXPIRES_AT)).first();
        return new AcquireResult.Exhausted(earliest == null ? Long.MAX_VALUE : earliest.getLong(EXPIRES_AT));
    }

    /**
     * A taken slot, flagged when another incarnation of the key held a live lease at the read. A lease read as live
     * that expired before the conditional update may be the very slot just taken over: it is no duplicate.
     */
    private static AcquireResult acquired(Document slot, Owner owner, AcquireReason reason, List<Integer> liveOfOtherIncarnations) {
        SlotLease lease = toLease(slot, owner);
        boolean duplicateOwnerKey = liveOfOtherIncarnations.stream().anyMatch(other -> other != lease.slot());
        return new AcquireResult.Acquired(lease, reason, duplicateOwnerKey);
    }

    /**
     * The §2.2 acquire change, after the stamp: {@code fence + 1}, the new owner, {@code floor = nextFloor},
     * {@code expiresAt = now + ttl}, {@code ceiling = max(expiresAt + tolerance, floor)}, {@code nextFloor = ceiling}.
     * Stages see the fields as the previous stage left them.
     */
    private static List<Bson> take(Owner owner, long ttlMillis, long toleranceMillis) {
        return List.of(
            STAMP,
            new Document("$set", new Document(FENCE, add("$" + FENCE, 1L))
                .append(OWNER_KEY, literal(owner.key()))
                .append(INCARNATION, literal(owner.incarnation().toString()))
                .append(LAST_OWNER_KEY, literal(owner.key()))
                .append(FLOOR, "$" + NEXT_FLOOR)
                .append(EXPIRES_AT, add("$" + WRITTEN_AT, ttlMillis))),
            new Document("$set", new Document(CEILING, max(add("$" + EXPIRES_AT, toleranceMillis), "$" + FLOOR))),
            new Document("$set", new Document(NEXT_FLOOR, "$" + CEILING)));
    }

    @Override
    public Optional<SlotLease> renew(SlotLease lease, Duration ttl, Duration tolerance) {
        Objects.requireNonNull(lease, "lease");
        long ttlMillis = positiveMillis(ttl, "ttl");
        long toleranceMillis = toleranceMillis(tolerance);
        // §2.2 renew: expiresAt = max(expiresAt, now + ttl); ceiling = max(ceiling, expiresAt + tolerance); nextFloor = ceiling.
        Document renewed = slots.findOneAndUpdate(holding(lease), List.of(
            STAMP,
            new Document("$set", new Document(EXPIRES_AT, max("$" + EXPIRES_AT, add("$" + WRITTEN_AT, ttlMillis)))),
            new Document("$set", new Document(CEILING, max("$" + CEILING, add("$" + EXPIRES_AT, toleranceMillis)))),
            new Document("$set", new Document(NEXT_FLOOR, "$" + CEILING))), AFTER);
        return renewed == null ? Optional.empty() : Optional.of(toLease(renewed, lease.owner()));
    }

    @Override
    public boolean release(SlotLease lease, long lastIssued) {
        Objects.requireNonNull(lease, "lease");
        // §2.2 release: no owner; expiresAt = now; nextFloor = max(floor, min(lastIssued, ceiling)); fence + 1.
        return slots.updateOne(holding(lease), List.of(
            STAMP,
            new Document("$set", new Document(OWNER_KEY, literal(null))
                .append(INCARNATION, literal(null))
                .append(EXPIRES_AT, "$" + WRITTEN_AT)
                .append(NEXT_FLOOR, max("$" + FLOOR, new Document("$min", List.of(lastIssued, "$" + CEILING))))
                .append(FENCE, add("$" + FENCE, 1L))))).getMatchedCount() > 0;
    }

    /** The CAS condition of renew and release: the slot, the incarnation and the fence of the lease. */
    private static Bson holding(SlotLease lease) {
        return Filters.and(Filters.eq(NS, lease.namespace()), Filters.eq(SLOT, lease.slot()),
            Filters.eq(INCARNATION, lease.owner().incarnation().toString()), Filters.eq(FENCE, lease.fence()));
    }

    /**
     * Records the namespace parameters on first use, rejects other ones, and creates the slots {@code [0, limit)}.
     * Each instance does it once per namespace.
     */
    private void ensureNamespace(String namespace, NamespaceParams params) {
        NamespaceParams checked = known.get(namespace);
        if (checked == null) {
            if (!indexed) {
                createIndexes();
                indexed = true;
            }
            checked = recordNamespace(namespace, params);
            if (checked.equals(params)) {
                provision(namespace, params.limit());
                known.putIfAbsent(namespace, checked);
            }
        }
        if (!checked.equals(params)) {
            throw new NamespaceMismatchException(namespace, "first used with limit " + checked.limit() + " and tolerance "
                + checked.toleranceMillis() + "ms, now " + params.limit() + " and " + params.toleranceMillis() + "ms");
        }
    }

    /** Creates the slot indexes, idempotently. */
    private void createIndexes() {
        slots.createIndex(Indexes.ascending(NS, SLOT), new IndexOptions().unique(true).name("ns_slot"));
        // The acquire read of the slots a key holds or held last.
        slots.createIndex(Indexes.ascending(NS, OWNER_KEY), new IndexOptions().name("ns_ownerKey"));
        slots.createIndex(Indexes.ascending(NS, LAST_OWNER_KEY), new IndexOptions().name("ns_lastOwnerKey"));
    }

    /**
     * Records the parameters of a new namespace, or reads those it was first used with. Concurrent first uses race on
     * the {@code _id} insert of the upsert, and the server does not retry the loser in every case: it gets {@code E11000},
     * then reads the winner's document back (or upserts again, when that write is not majority-visible yet).
     */
    private NamespaceParams recordNamespace(String namespace, NamespaceParams params) {
        Bson id = Filters.eq(MongoRangeStore.ID, namespace);
        Bson setOnInsert = Updates.combine(Updates.setOnInsert(LIMIT, params.limit()), Updates.setOnInsert(TOLERANCE_MS, params.toleranceMillis()));
        for (int attempt = 1; ; attempt++) {
            Document stored;
            try {
                stored = namespaces.findOneAndUpdate(id, setOnInsert, new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
            } catch (MongoServerException e) {
                if (!MongoStore.isDuplicateKey(e) || attempt >= NAMESPACE_ATTEMPTS) {
                    throw e;
                }
                stored = namespaces.find(id).first();
            }
            if (stored != null) {
                return new NamespaceParams(stored.getInteger(LIMIT), stored.getLong(TOLERANCE_MS));
            }
        }
    }

    /** Creates the missing slot documents; ones another instance created first are kept. */
    private void provision(String namespace, int limit) {
        if (slots.countDocuments(Filters.eq(NS, namespace)) >= limit) {
            return;
        }
        List<Document> free = new ArrayList<>(limit);
        for (int slot = 0; slot < limit; slot++) {
            free.add(new Document(NS, namespace).append(SLOT, slot)
                .append(OWNER_KEY, null).append(INCARNATION, null).append(LAST_OWNER_KEY, null)
                .append(FENCE, 0L).append(EXPIRES_AT, 0L).append(FLOOR, 0L).append(CEILING, 0L).append(NEXT_FLOOR, 0L)
                .append(WRITTEN_AT, 0L));
        }
        try {
            slots.insertMany(free, new InsertManyOptions().ordered(false));
        } catch (MongoBulkWriteException e) {
            if (e.getWriteErrors().stream().anyMatch(error -> error.getCode() != MongoStore.DUPLICATE_KEY) || e.getWriteConcernError() != null) {
                throw e;
            }
        }
    }

    private static SlotLease toLease(Document slot, Owner owner) {
        return toLease(slot, owner, slot.getLong(WRITTEN_AT));
    }

    private static SlotLease toLease(Document slot, Owner owner, long storeNow) {
        return new SlotLease(slot.getString(NS), slot.getInteger(SLOT), owner, slot.getLong(FENCE), slot.getLong(EXPIRES_AT),
            slot.getLong(FLOOR), slot.getLong(CEILING), storeNow);
    }

    /** A constant in a pipeline stage: a key starting with {@code $} stays a string, never a field path. */
    private static Document literal(Object value) {
        return new Document("$literal", value);
    }

    private static Document add(Object left, long right) {
        return new Document("$add", List.of(left, right));
    }

    private static Document max(Object left, Object right) {
        return new Document("$max", List.of(left, right));
    }

    private static long positiveMillis(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive: " + duration);
        }
        return duration.toMillis();
    }

    private static long toleranceMillis(Duration tolerance) {
        Objects.requireNonNull(tolerance, "tolerance");
        if (tolerance.isNegative()) {
            throw new IllegalArgumentException("tolerance must not be negative: " + tolerance);
        }
        return tolerance.toMillis();
    }

    private record NamespaceParams(int limit, long toleranceMillis) {
    }
}
