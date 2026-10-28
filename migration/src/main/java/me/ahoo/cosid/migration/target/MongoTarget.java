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

package me.ahoo.cosid.migration.target;

import me.ahoo.cosid.migration.StoreLocation;
import me.ahoo.cosid.migration.source.MongoSource;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.ReadConcern;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The 5.0 MongoDB store (06 §2.3): {@code cosid_range {_id: key, next, sealed}}, {@code cosid_namespace {_id: ns,
 * limit, toleranceMs}} and {@code cosid_slot {ns, slot, ownerKey, incarnation, lastOwnerKey, fence, expiresAt, floor,
 * ceiling, nextFloor, writtenAt}}, with the field types store/mongo reads (int32 {@code slot} and {@code limit}, int64
 * otherwise), in the database {@code options.database} ({@code cosid_db}), {@code majority} concerns.
 */
public final class MongoTarget implements TargetAdmin {
    private final MongoClient client;
    private final MongoDatabase database;
    private final MongoCollection<Document> ranges;
    private final MongoCollection<Document> namespaces;
    private final MongoCollection<Document> slots;

    public MongoTarget(StoreLocation location) {
        this.client = MongoSource.client(location);
        this.database = client.getDatabase(MongoSource.database(location)).withWriteConcern(WriteConcern.MAJORITY).withReadConcern(ReadConcern.MAJORITY);
        this.ranges = database.getCollection("cosid_range");
        this.namespaces = database.getCollection("cosid_namespace");
        this.slots = database.getCollection("cosid_slot");
    }

    @Override
    public long now() {
        Document now = database.aggregate(List.of(new Document("$documents", List.of(new Document())),
            new Document("$project", new Document("now", new Document("$toLong", "$$NOW"))))).first();
        return ((Number) Objects.requireNonNull(now, "$$NOW").get("now")).longValue();
    }

    @Override
    public Optional<RangeState> range(String key) {
        return Optional.ofNullable(ranges.find(Filters.eq("_id", key)).first()).map(MongoTarget::toRange);
    }

    private static RangeState toRange(Document document) {
        Object next = document.get("next");
        return new RangeState(String.valueOf(document.get("_id")), next instanceof Number number ? number.longValue() : 0,
            Boolean.TRUE.equals(document.get("sealed")));
    }

    @Override
    public RangeState raise(String key, long next) {
        Document max = new Document("$max", List.of(new Document("$ifNull", List.of("$next", next)), next));
        ranges.updateOne(Filters.eq("_id", key), List.of(new Document("$set", new Document("next", max))), new UpdateOptions().upsert(true));
        return range(key).orElseThrow();
    }

    @Override
    public List<RangeState> ranges(String prefix) {
        List<RangeState> found = new ArrayList<>();
        for (Document document : ranges.find(Filters.regex("_id", "^" + Pattern.quote(prefix))).sort(Sorts.ascending("_id"))) {
            found.add(toRange(document));
        }
        return found;
    }

    @Override
    public boolean delete(String key) {
        return ranges.deleteOne(Filters.and(Filters.eq("_id", key), Filters.eq("sealed", true))).getDeletedCount() > 0;
    }

    @Override
    public Optional<NamespaceState> namespace(String namespace) {
        Document meta = namespaces.find(Filters.eq("_id", namespace)).first();
        if (meta == null) {
            return Optional.empty();
        }
        int limit = ((Number) meta.get("limit")).intValue();
        long tolerance = ((Number) meta.get("toleranceMs")).longValue();
        long now = now();
        int count = 0;
        int live = 0;
        long minNextFloor = Long.MAX_VALUE;
        for (Document slot : slots.find(Filters.and(Filters.eq("ns", namespace), Filters.lt("slot", limit)))) {
            count++;
            minNextFloor = Math.min(minNextFloor, ((Number) slot.get("nextFloor")).longValue());
            if (slot.get("incarnation") != null && ((Number) slot.get("expiresAt")).longValue() >= now) {
                live++;
            }
        }
        return Optional.of(new NamespaceState(limit, tolerance, count, minNextFloor, live));
    }

    @Override
    public NamespaceState prepare(String namespace, int limit, long toleranceMillis, long nextFloor) {
        slots.createIndex(Indexes.ascending("ns", "slot"), new IndexOptions().unique(true).name("ns_slot"));
        slots.createIndex(Indexes.ascending("ns", "ownerKey"), new IndexOptions().name("ns_ownerKey"));
        slots.createIndex(Indexes.ascending("ns", "lastOwnerKey"), new IndexOptions().name("ns_lastOwnerKey"));
        namespaces.updateOne(Filters.eq("_id", namespace), new Document("$setOnInsert", new Document("limit", limit).append("toleranceMs", toleranceMillis)),
            new UpdateOptions().upsert(true));
        NamespaceState before = namespace(namespace).orElseThrow();
        if (before.limit() != limit || before.toleranceMillis() != toleranceMillis) {
            throw TargetAdmin.mismatch(namespace, limit, toleranceMillis, before.limit(), before.toleranceMillis());
        }
        if (before.live() > 0) {
            throw TargetAdmin.live(namespace, before.live());
        }
        Set<Integer> existing = new HashSet<>();
        for (Document slot : slots.find(Filters.and(Filters.eq("ns", namespace), Filters.lt("slot", limit)))) {
            existing.add(((Number) slot.get("slot")).intValue());
        }
        for (int slot = 0; slot < limit; slot++) {
            if (existing.contains(slot)) {
                continue;
            }
            try {
                slots.insertOne(new Document("ns", namespace).append("slot", slot)
                    .append("ownerKey", null).append("incarnation", null).append("lastOwnerKey", null)
                    .append("fence", 0L).append("expiresAt", 0L).append("floor", 0L).append("ceiling", 0L).append("nextFloor", nextFloor)
                    .append("writtenAt", 0L));
            } catch (MongoWriteException e) {
                if (e.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) {
                    throw e;
                }
            }
        }
        slots.updateMany(Filters.and(Filters.eq("ns", namespace), Filters.lt("slot", limit), Filters.lt("nextFloor", nextFloor)),
            new Document("$set", new Document("nextFloor", nextFloor)));
        return namespace(namespace).orElseThrow();
    }

    @Override
    public void close() {
        client.close();
    }
}
