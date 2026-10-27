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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Slot leases in {@code cosid_slot} (05-machine-lease.md §2, 06-stores.md §2.1).
 *
 * <ul>
 *     <li>The first {@code acquire} of a namespace records {@code {limit, tolerance}} in {@code cosid_namespace} and
 *     creates its {@code limit} slot rows, idempotently; a store instance remembers the namespaces it checked.</li>
 *     <li>{@code acquire} reads the rows of the owner key once (the idempotent retry, and whether another incarnation
 *     of the key is live), then tries the preferred, the sticky and the lowest free slot in that order, each in a
 *     {@code READ COMMITTED} transaction that locks one candidate with {@link JdbcDialect#lockFirst} (skipping rows
 *     other acquirers hold) and updates it under its fence.</li>
 *     <li>{@code renew} and {@code release} read the row of the lease and write it back with a compare-and-set on
 *     incarnation, fence, expiry and ceiling; a lost race reads again.</li>
 * </ul>
 *
 * <p>Every write is stamped by the store clock ({@link JdbcDialect#nowMillis()}) read in the statement that the
 * write is computed from, so {@code expiresAt − storeNow == ttl}. Uniqueness does not depend on that clock (05 §3).
 */
final class JdbcSlotLeaseStore implements SlotLeaseStore {
    /** How long an acquire waits between attempts that found free slots locked by concurrent acquirers. */
    private static final long BUSY_PAUSE_NANOS = TimeUnit.MILLISECONDS.toNanos(5);
    /** How long an acquire keeps trying when free slots stay locked and the store has no timeout. */
    private static final Duration DEFAULT_BUSY_WAIT = Duration.ofSeconds(10);
    private static final String LEASE_COLUMNS = "slot, fence, expires_at, floor_ms, ceiling_ms, next_floor_ms";
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
    public AcquireResult acquire(String namespace, Owner owner, int limit, Duration ttl, Duration tolerance, OptionalInt preferredSlot) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(preferredSlot, "preferredSlot");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive: " + limit);
        }
        long ttlMillis = positiveMillis(ttl, "ttl");
        long toleranceMillis = toleranceMillis(tolerance);
        Namespace ns = new Namespace(limit, toleranceMillis);
        ensureNamespace(namespace, ns);
        Duration busyWait = jdbc.timeout.isZero() ? DEFAULT_BUSY_WAIT : jdbc.timeout;
        long deadline = System.nanoTime() + busyWait.toNanos();
        while (true) {
            Attempt attempt = jdbc.run("acquire in " + namespace, session -> attempt(session, namespace, owner, ns, ttlMillis, preferredSlot));
            if (attempt.result != null) {
                return attempt.result;
            }
            if (System.nanoTime() - deadline > 0) {
                // Free slots stayed locked: tell the caller to come back right away.
                return new AcquireResult.Exhausted(attempt.storeNow);
            }
            LockSupport.parkNanos(BUSY_PAUSE_NANOS);
        }
    }

    private Attempt attempt(JdbcSession session, String namespace, Owner owner, Namespace ns, long ttlMillis, OptionalInt preferredSlot) throws SQLException {
        String incarnation = owner.incarnation().toString();
        // §2.1 step 1, duplicateOwnerKey, and whether step 3 has a candidate: the rows of this owner key, in one read.
        boolean duplicate = false;
        boolean stickyCandidate = false;
        SlotLease held = null;
        try (PreparedStatement select = session.prepare("select " + LEASE_COLUMNS + ", owner_key, incarnation, case when " + free + " then 1 else 0 end as free_now, "
            + now + " as store_now from " + session.tables().slot() + " where namespace = ? and slot < ? and (owner_key = ? or last_owner_key = ?)")) {
            select.setString(1, namespace);
            select.setInt(2, ns.limit);
            select.setString(3, owner.key());
            select.setString(4, owner.key());
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    long storeNow = rs.getLong("store_now");
                    boolean freeNow = rs.getInt("free_now") == 1;
                    stickyCandidate |= freeNow;
                    if (!owner.key().equals(rs.getString("owner_key"))) {
                        continue;
                    }
                    String rowIncarnation = rs.getString("incarnation");
                    if (rowIncarnation != null && incarnation.equals(rowIncarnation.strip())) {
                        held = lease(rs, namespace, owner, storeNow);
                    } else if (!freeNow) {
                        duplicate = true;
                    }
                }
            }
        }
        if (held != null) {
            return new Attempt(new AcquireResult.Acquired(held, AcquireReason.IDEMPOTENT, duplicate), held.storeNow());
        }
        if (preferredSlot.isPresent() && preferredSlot.getAsInt() >= 0 && preferredSlot.getAsInt() < ns.limit) {
            int preferred = preferredSlot.getAsInt();
            SlotLease lease = take(session, namespace, owner, ns, ttlMillis, "namespace = ? and slot = ? and " + free, select -> {
                select.setString(1, namespace);
                select.setInt(2, preferred);
            });
            if (lease != null) {
                return new Attempt(new AcquireResult.Acquired(lease, AcquireReason.PREFERRED, duplicate), lease.storeNow());
            }
        }
        SlotLease sticky = !stickyCandidate ? null : take(session, namespace, owner, ns, ttlMillis, "namespace = ? and slot < ? and last_owner_key = ? and " + free,
            select -> {
                select.setString(1, namespace);
                select.setInt(2, ns.limit);
                select.setString(3, owner.key());
            });
        if (sticky != null) {
            return new Attempt(new AcquireResult.Acquired(sticky, AcquireReason.STICKY, duplicate), sticky.storeNow());
        }
        SlotLease lowest = take(session, namespace, owner, ns, ttlMillis, "namespace = ? and slot < ? and " + free, select -> {
            select.setString(1, namespace);
            select.setInt(2, ns.limit);
        });
        if (lowest != null) {
            return new Attempt(new AcquireResult.Acquired(lowest, AcquireReason.LOWEST, duplicate), lowest.storeNow());
        }
        // Step 5, unless a free slot was only skipped because a concurrent acquire had it locked.
        try (PreparedStatement select = session.prepare("select min(expires_at), sum(case when " + free + " then 1 else 0 end), " + now
            + " from " + session.tables().slot() + " where namespace = ? and slot < ?")) {
            select.setString(1, namespace);
            select.setInt(2, ns.limit);
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                long earliestExpiresAt = rs.getLong(1);
                long freeSlots = rs.getLong(2);
                long storeNow = rs.getLong(3);
                return freeSlots > 0 ? new Attempt(null, storeNow) : new Attempt(new AcquireResult.Exhausted(earliestExpiresAt), storeNow);
            }
        }
    }

    /**
     * One step of the slot choice: locks the first free row matching {@code where} and takes it under its fence (05
     * §2.2: {@code fence + 1}, {@code floor = next_floor}, {@code ceiling = max(expiresAt + tolerance, floor)}).
     *
     * @return the lease, or null if no row matched
     */
    private SlotLease take(JdbcSession session, String namespace, Owner owner, Namespace ns, long ttlMillis, String where, Binder binder) throws SQLException {
        String table = session.tables().slot();
        return session.transaction(() -> {
            int slot;
            long fence;
            long floor;
            long storeNow;
            try (PreparedStatement select = session.prepare(jdbc.dialect.lockFirst(LEASE_COLUMNS + ", " + now + " as store_now", table, where, "slot"))) {
                binder.bind(select);
                try (ResultSet rs = select.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    slot = rs.getInt("slot");
                    fence = rs.getLong("fence");
                    floor = rs.getLong("next_floor_ms");
                    storeNow = rs.getLong("store_now");
                }
            }
            long expiresAt = storeNow + ttlMillis;
            long ceiling = Math.max(expiresAt + ns.toleranceMillis, floor);
            try (PreparedStatement update = session.prepare("update " + table + " set owner_key = ?, incarnation = ?, last_owner_key = ?, fence = ?,"
                + " expires_at = ?, floor_ms = ?, ceiling_ms = ?, next_floor_ms = ? where namespace = ? and slot = ? and fence = ?")) {
                update.setString(1, owner.key());
                update.setString(2, owner.incarnation().toString());
                update.setString(3, owner.key());
                update.setLong(4, fence + 1);
                update.setLong(5, expiresAt);
                update.setLong(6, floor);
                update.setLong(7, ceiling);
                update.setLong(8, ceiling);
                update.setString(9, namespace);
                update.setInt(10, slot);
                update.setLong(11, fence);
                if (update.executeUpdate() != 1) {
                    return null;
                }
            }
            return new SlotLease(namespace, slot, owner, fence + 1, expiresAt, floor, ceiling, storeNow);
        });
    }

    @Override
    public Optional<SlotLease> renew(SlotLease lease, Duration ttl, Duration tolerance) {
        Objects.requireNonNull(lease, "lease");
        long ttlMillis = positiveMillis(ttl, "ttl");
        long toleranceMillis = toleranceMillis(tolerance);
        return jdbc.run("renew " + lease.namespace() + "/" + lease.slot(), session -> {
            while (true) {
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
                        return Optional.of(new SlotLease(lease.namespace(), lease.slot(), lease.owner(), lease.fence(), expiresAt, current.floor, ceiling,
                            current.storeNow));
                    }
                }
            }
        });
    }

    @Override
    public boolean release(SlotLease lease, long lastIssued) {
        Objects.requireNonNull(lease, "lease");
        return jdbc.run("release " + lease.namespace() + "/" + lease.slot(), session -> {
            while (true) {
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

    private static SlotLease lease(ResultSet rs, String namespace, Owner owner, long storeNow) throws SQLException {
        return new SlotLease(namespace, rs.getInt("slot"), owner, rs.getLong("fence"), rs.getLong("expires_at"), rs.getLong("floor_ms"),
            rs.getLong("ceiling_ms"), storeNow);
    }

    /**
     * Records the namespace parameters on first use, rejects others, and makes sure its slot rows exist.
     */
    private void ensureNamespace(String namespace, Namespace requested) {
        Namespace known = namespaces.get(namespace);
        if (known == null) {
            known = jdbc.run("prepare namespace " + namespace, session -> {
                try {
                    jdbc.dialect.insertNamespaceIfAbsent(session, namespace, requested.limit, requested.toleranceMillis);
                } catch (SQLException e) {
                    if (!jdbc.dialect.isDuplicateKey(e)) {
                        throw e;
                    }
                }
                Namespace stored = stored(session, namespace);
                if (stored.equals(requested) && slotRows(session, namespace) < stored.limit) {
                    jdbc.retryingDuplicates(() -> {
                        jdbc.dialect.provisionSlots(session, namespace, stored.limit);
                        return null;
                    });
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

    /** One attempt of an acquire: its result, or null to try again; the store clock it last read. */
    private record Attempt(AcquireResult result, long storeNow) {
    }

    private record Namespace(int limit, long toleranceMillis) {
    }

    private record Current(long expiresAt, long floor, long ceiling, long storeNow) {
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
