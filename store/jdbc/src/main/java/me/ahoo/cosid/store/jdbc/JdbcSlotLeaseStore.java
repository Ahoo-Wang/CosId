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

package me.ahoo.cosid.store.jdbc;

import me.ahoo.cosid.NamespaceMismatchException;
import me.ahoo.cosid.lease.AcquireReason;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.lease.SlotLeaseStore;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Slot leases in {@code cosid_slot} (05-machine-lease.md §2, 06-stores.md §2.1).
 *
 * <ul>
 *     <li>The first {@code acquire} of a namespace records {@code {limit, tolerance}} in {@code cosid_namespace} and
 *     creates its {@code limit} slot rows, idempotently; a store instance remembers the namespaces it checked.</li>
 *     <li>{@code acquire} reads its candidates once without locking (the rows of the owner key, the preferred slot
 *     and the first free slots), then takes the first of §2.1's order that a compare-and-set update still finds free:
 *     the idempotent retry, preferred, sticky, lowest. Uniqueness only needs that per-slot atomic update; there is no
 *     transaction or row lock, so a fresh acquire is two round trips.</li>
 *     <li>{@code renew} and {@code release} read the row of the lease and write it back with a compare-and-set on
 *     incarnation, fence, expiry and ceiling; a lost race reads again.</li>
 * </ul>
 *
 * <p>Every write is stamped by the store clock ({@link JdbcDialect#nowMillis()}) read in the statement that the
 * write is computed from, so {@code expiresAt − storeNow == ttl}. Uniqueness does not depend on that clock (05 §3).
 */
final class JdbcSlotLeaseStore implements SlotLeaseStore {
    /** How long a concurrent first use waits for the slot rows the first user creates. */
    private static final Duration PROVISION_WAIT = Duration.ofSeconds(3);
    private static final long PROVISION_POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(20);
    /** How many free slots one acquire reads as candidates of the lowest step. */
    static final int FREE_CANDIDATES = 8;
    private final Jdbc jdbc;
    private final String now;
    private final String free;
    private final Map<String, Namespace> namespaces = new ConcurrentHashMap<>();

    JdbcSlotLeaseStore(Jdbc jdbc) {
        this.jdbc = jdbc;
        this.now = jdbc.dialect.nowMillis();
        this.free = "(owner_key is null or expires_at < " + now + ")";
    }

    @Override
    public AcquireResult acquire(String namespace, Owner owner, int limit, Duration ttl, Duration tolerance, int slotOffset, OptionalInt preferredSlot) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(preferredSlot, "preferredSlot");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive: " + limit);
        }
        jdbc.dialect.checkKey("namespace", namespace);
        jdbc.dialect.checkKey("owner key", owner.key());
        long ttlMillis = positiveMillis(ttl, "ttl");
        long toleranceMillis = toleranceMillis(tolerance);
        Namespace ns = new Namespace(limit, toleranceMillis);
        ensureNamespace(namespace, ns);
        int preferred = preferredSlot.isPresent() && preferredSlot.getAsInt() >= 0 && preferredSlot.getAsInt() < limit ? preferredSlot.getAsInt() : -1;
        String operation = "acquire in " + namespace;
        long deadline = jdbc.deadline();
        return jdbc.run(operation, session -> {
            while (true) {
                jdbc.checkDeadline(deadline, operation);
                AcquireResult result = attempt(session, namespace, owner, ns, ttlMillis, preferred);
                if (result != null) {
                    return result;
                }
                // Every candidate was taken or renewed by someone else between the read and the update: read again.
            }
        });
    }

    /**
     * One read of the candidates, then one compare-and-set per candidate in the order of §2.1.
     *
     * @return the result, or null when every candidate was lost to a concurrent write
     */
    private AcquireResult attempt(JdbcSession session, String namespace, Owner owner, Namespace ns, long ttlMillis, int preferred) throws SQLException {
        Map<Integer, Candidate> candidates = candidates(session, namespace, owner, ns, preferred);
        String incarnation = owner.incarnation().toString();
        boolean duplicate = false;
        Candidate held = null;
        for (Candidate candidate : candidates.values()) {
            if (owner.key().equals(candidate.ownerKey)) {
                if (incarnation.equals(candidate.incarnation)) {
                    held = candidate;
                } else if (!candidate.free) {
                    duplicate = true;
                }
            }
        }
        // §2.1 step 1: the owner, same key and incarnation, already holds a slot.
        if (held != null) {
            SlotLease lease = SlotLease.builder()
                .namespace(namespace)
                .slot(held.slot)
                .owner(owner)
                .fence(held.fence)
                .expiresAt(held.expiresAt)
                .floor(held.floor)
                .ceiling(held.ceiling)
                .storeNow(held.storeNow)
                .build();
            return AcquireResult.Acquired.builder(lease, AcquireReason.IDEMPOTENT).duplicateOwnerKey(duplicate).build();
        }
        if (candidates.isEmpty()) {
            return new AcquireResult.Exhausted(earliestExpiresAt(session, namespace, ns));
        }
        // Step 2: the preferred slot.
        Candidate preferredCandidate = candidates.get(preferred);
        if (preferredCandidate != null && preferredCandidate.free) {
            SlotLease lease = take(session, namespace, owner, ns, ttlMillis, preferredCandidate);
            if (lease != null) {
                return AcquireResult.Acquired.builder(lease, AcquireReason.PREFERRED).duplicateOwnerKey(duplicate).build();
            }
        }
        // Step 3: a free slot the owner key held last, lowest first.
        for (Candidate candidate : candidates.values()) {
            if (candidate.free && owner.key().equals(candidate.lastOwnerKey)) {
                SlotLease lease = take(session, namespace, owner, ns, ttlMillis, candidate);
                if (lease != null) {
                    return AcquireResult.Acquired.builder(lease, AcquireReason.STICKY).duplicateOwnerKey(duplicate).build();
                }
            }
        }
        // Step 4: the lowest free slot; after losing it to a concurrent acquire, a random one of the next free ones, so
        // that racing acquirers spread out instead of all retrying the same row ("lowest" is best effort, §2.1).
        List<Candidate> free = new ArrayList<>();
        for (Candidate candidate : candidates.values()) {
            if (candidate.free) {
                free.add(candidate);
            }
        }
        if (free.isEmpty()) {
            return new AcquireResult.Exhausted(earliestExpiresAt(session, namespace, ns));
        }
        if (free.size() > 2) {
            Collections.shuffle(free.subList(1, free.size()), ThreadLocalRandom.current());
        }
        for (Candidate candidate : free) {
            SlotLease lease = take(session, namespace, owner, ns, ttlMillis, candidate);
            if (lease != null) {
                return AcquireResult.Acquired.builder(lease, AcquireReason.LOWEST).duplicateOwnerKey(duplicate).build();
            }
        }
        return null;
    }

    /**
     * The candidates of one acquire in one non-locking read, by slot: the rows of the owner key (held or sticky), the
     * preferred slot, and the first {@link #FREE_CANDIDATES} free slots, each with the store clock of the read.
     */
    private Map<Integer, Candidate> candidates(JdbcSession session, String namespace, Owner owner, Namespace ns, int preferred) throws SQLException {
        String table = session.tables().slot();
        String columns = "slot, fence, owner_key, incarnation, last_owner_key, expires_at, floor_ms, ceiling_ms, next_floor_ms, case when " + free
            + " then 1 else 0 end as free_now, " + now + " as store_now";
        String sql = "select * from (select " + columns + " from " + table
            + " where namespace = ? and slot < ? and (owner_key = ? or last_owner_key = ? or slot = ?)) o union all select * from ("
            + jdbc.dialect.firstRows(columns, table, "namespace = ? and slot < ? and " + free, "slot", FREE_CANDIDATES) + ") f";
        Map<Integer, Candidate> candidates = new TreeMap<>();
        try (PreparedStatement select = session.prepare(sql)) {
            select.setString(1, namespace);
            select.setInt(2, ns.limit);
            select.setString(3, owner.key());
            select.setString(4, owner.key());
            select.setInt(5, preferred);
            select.setString(6, namespace);
            select.setInt(7, ns.limit);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    String incarnation = rs.getString("incarnation");
                    Candidate candidate = new Candidate(rs.getInt("slot"), rs.getLong("fence"), rs.getString("owner_key"),
                        incarnation == null ? null : incarnation.strip(), rs.getString("last_owner_key"), rs.getLong("expires_at"), rs.getLong("floor_ms"),
                        rs.getLong("ceiling_ms"), rs.getLong("next_floor_ms"), rs.getInt("free_now") == 1, rs.getLong("store_now"));
                    candidates.putIfAbsent(candidate.slot, candidate);
                }
            }
        }
        return candidates;
    }

    private long earliestExpiresAt(JdbcSession session, String namespace, Namespace ns) throws SQLException {
        try (PreparedStatement select = session.prepare("select min(expires_at) from " + session.tables().slot() + " where namespace = ? and slot < ?")) {
            select.setString(1, namespace);
            select.setInt(2, ns.limit);
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /**
     * Takes a free candidate with one compare-and-set (05 §2.2: {@code fence + 1}, {@code floor = next_floor},
     * {@code ceiling = max(expiresAt + tolerance, floor)}): the update matches only while the row still has the fence
     * and next floor that were read and is still free by the store clock. A renewal in between makes the row live, a
     * takeover or release raises the fence, so a matched update always starts from the row as read.
     *
     * @return the lease, or null if the row changed since the read
     */
    private SlotLease take(JdbcSession session, String namespace, Owner owner, Namespace ns, long ttlMillis, Candidate candidate) throws SQLException {
        long floor = candidate.nextFloor;
        long expiresAt = candidate.storeNow + ttlMillis;
        long ceiling = Math.max(expiresAt + ns.toleranceMillis, floor);
        try (PreparedStatement update = session.prepare("update " + session.tables().slot() + " set owner_key = ?, incarnation = ?, last_owner_key = ?,"
            + " fence = ?, expires_at = ?, floor_ms = ?, ceiling_ms = ?, next_floor_ms = ?"
            + " where namespace = ? and slot = ? and fence = ? and next_floor_ms = ? and " + free)) {
            update.setString(1, owner.key());
            update.setString(2, owner.incarnation().toString());
            update.setString(3, owner.key());
            update.setLong(4, candidate.fence + 1);
            update.setLong(5, expiresAt);
            update.setLong(6, floor);
            update.setLong(7, ceiling);
            update.setLong(8, ceiling);
            update.setString(9, namespace);
            update.setInt(10, candidate.slot);
            update.setLong(11, candidate.fence);
            update.setLong(12, floor);
            if (update.executeUpdate() != 1) {
                return null;
            }
        }
        return SlotLease.builder()
            .namespace(namespace)
            .slot(candidate.slot)
            .owner(owner)
            .fence(candidate.fence + 1)
            .expiresAt(expiresAt)
            .floor(floor)
            .ceiling(ceiling)
            .storeNow(candidate.storeNow)
            .build();
    }

    @Override
    public Optional<SlotLease> renew(SlotLease lease, Duration ttl, Duration tolerance) {
        Objects.requireNonNull(lease, "lease");
        long ttlMillis = positiveMillis(ttl, "ttl");
        long toleranceMillis = toleranceMillis(tolerance);
        String operation = "renew " + lease.namespace() + "/" + lease.slot();
        long deadline = jdbc.deadline();
        return jdbc.run(operation, session -> {
            while (true) {
                jdbc.checkDeadline(deadline, operation);
                Current current = current(session, lease);
                if (current == null) {
                    return Optional.empty();
                }
                long expiresAt = Math.max(current.expiresAt, current.storeNow + ttlMillis);
                long ceiling = Math.max(current.ceiling, expiresAt + toleranceMillis);
                try (PreparedStatement update = session.prepare("update " + session.tables().slot() + " set expires_at = ?, ceiling_ms = ?, next_floor_ms = ?"
                    + " where namespace = ? and slot = ? and incarnation = ? and fence = ? and expires_at = ? and ceiling_ms = ?")) {
                    update.setLong(1, expiresAt);
                    update.setLong(2, ceiling);
                    update.setLong(3, ceiling);
                    bindCurrent(update, 4, lease, current);
                    if (update.executeUpdate() == 1) {
                        return Optional.of(SlotLease.builder()
                            .namespace(lease.namespace())
                            .slot(lease.slot())
                            .owner(lease.owner())
                            .fence(lease.fence())
                            .expiresAt(expiresAt)
                            .floor(current.floor)
                            .ceiling(ceiling)
                            .storeNow(current.storeNow)
                            .build());
                    }
                }
            }
        });
    }

    @Override
    public boolean release(SlotLease lease, long lastIssued) {
        Objects.requireNonNull(lease, "lease");
        String operation = "release " + lease.namespace() + "/" + lease.slot();
        long deadline = jdbc.deadline();
        return jdbc.run(operation, session -> {
            while (true) {
                jdbc.checkDeadline(deadline, operation);
                Current current = current(session, lease);
                if (current == null) {
                    return false;
                }
                long nextFloor = Math.max(current.floor, Math.min(lastIssued, current.ceiling));
                try (PreparedStatement update = session.prepare("update " + session.tables().slot() + " set owner_key = null, incarnation = null,"
                    + " expires_at = ?, next_floor_ms = ?, fence = fence + 1"
                    + " where namespace = ? and slot = ? and incarnation = ? and fence = ? and expires_at = ? and ceiling_ms = ?")) {
                    update.setLong(1, current.storeNow);
                    update.setLong(2, nextFloor);
                    bindCurrent(update, 3, lease, current);
                    if (update.executeUpdate() == 1) {
                        return true;
                    }
                }
            }
        });
    }

    /**
     * The row of a lease as long as the lease still names the holding (incarnation and fence match), with the store
     * clock of the read.
     */
    private Current current(JdbcSession session, SlotLease lease) throws SQLException {
        try (PreparedStatement select = session.prepare("select expires_at, floor_ms, ceiling_ms, " + now + " from " + session.tables().slot()
            + " where namespace = ? and slot = ? and incarnation = ? and fence = ?")) {
            select.setString(1, lease.namespace());
            select.setInt(2, lease.slot());
            select.setString(3, lease.owner().incarnation().toString());
            select.setLong(4, lease.fence());
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? new Current(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)) : null;
            }
        }
    }

    private static void bindCurrent(PreparedStatement update, int from, SlotLease lease, Current current) throws SQLException {
        update.setString(from, lease.namespace());
        update.setInt(from + 1, lease.slot());
        update.setString(from + 2, lease.owner().incarnation().toString());
        update.setLong(from + 3, lease.fence());
        update.setLong(from + 4, current.expiresAt);
        update.setLong(from + 5, current.ceiling);
    }

    /**
     * Records the namespace parameters on first use, rejects others, and makes sure its slot rows exist.
     */
    private void ensureNamespace(String namespace, Namespace requested) {
        Namespace known = namespaces.get(namespace);
        if (known == null) {
            known = jdbc.run("prepare namespace " + namespace, session -> {
                boolean inserted;
                try {
                    inserted = jdbc.dialect.insertNamespaceIfAbsent(session, namespace, requested.limit, requested.toleranceMillis);
                } catch (SQLException e) {
                    if (!jdbc.dialect.isDuplicateKey(e)) {
                        throw e;
                    }
                    inserted = false;
                }
                Namespace stored = stored(session, namespace);
                if (stored.equals(requested)) {
                    provision(session, namespace, stored.limit, inserted);
                }
                return stored;
            });
            if (known.equals(requested)) {
                namespaces.put(namespace, known);
            }
        }
        if (!known.equals(requested)) {
            throw new NamespaceMismatchException(namespace, "first used with limit " + known.limit + " and tolerance " + known.toleranceMillis
                + "ms, now " + requested.limit + " and " + requested.toleranceMillis + "ms");
        }
    }

    /**
     * Makes sure the slot rows of a namespace exist. Only the first user, which inserted the namespace row, creates
     * them, so concurrent first uses do not all insert the same rows at once (on SQL Server they queued past the
     * timeout); the others wait for the rows, and create the missing ones themselves only if they do not appear within
     * {@link #PROVISION_WAIT} (or the store timeout, if shorter), as when the first user died in between.
     */
    private void provision(JdbcSession session, String namespace, int limit, boolean first) throws SQLException {
        if (!first) {
            Duration wait = jdbc.timeout.isZero() || jdbc.timeout.compareTo(PROVISION_WAIT) > 0 ? PROVISION_WAIT : jdbc.timeout;
            long deadline = System.nanoTime() + wait.toNanos();
            while (slotRows(session, namespace) < limit) {
                if (System.nanoTime() - deadline > 0) {
                    break;
                }
                LockSupport.parkNanos(PROVISION_POLL_NANOS);
            }
        }
        if (slotRows(session, namespace) < limit) {
            jdbc.retryingDuplicates(() -> {
                jdbc.dialect.provisionSlots(session, namespace, limit);
                return null;
            });
        }
    }

    private static Namespace stored(JdbcSession session, String namespace) throws SQLException {
        try (PreparedStatement select = session.prepare("select slot_limit, tolerance_ms from " + session.tables().namespace() + " where namespace = ?")) {
            select.setString(1, namespace);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Namespace " + namespace + " is missing from " + session.tables().namespace() + " right after its insert");
                }
                return new Namespace(rs.getInt(1), rs.getLong(2));
            }
        }
    }

    private static long slotRows(JdbcSession session, String namespace) throws SQLException {
        try (PreparedStatement select = session.prepare("select count(*) from " + session.tables().slot() + " where namespace = ?")) {
            select.setString(1, namespace);
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
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

    /** A slot row as one acquire read it, with the store clock of the read. */
    private record Candidate(int slot, long fence, String ownerKey, String incarnation, String lastOwnerKey, long expiresAt, long floor, long ceiling,
                             long nextFloor, boolean free, long storeNow) {
    }

    private record Namespace(int limit, long toleranceMillis) {
    }

    private record Current(long expiresAt, long floor, long ceiling, long storeNow) {
    }

}
