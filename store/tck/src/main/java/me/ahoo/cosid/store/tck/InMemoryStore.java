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

package me.ahoo.cosid.store.tck;

import me.ahoo.cosid.NamespaceMismatchException;
import me.ahoo.cosid.RangeExhaustedException;
import me.ahoo.cosid.RangeSealedException;
import me.ahoo.cosid.lease.AcquireReason;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.time.TimeSource;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The in-memory reference store (06-stores.md §3): passes every contract, for the unit tests of core and Spring.
 * Instances are independent; {@link #ranges()} and {@link #slots()} return this store. Thread-safe: every operation
 * is atomic on one key or one namespace, stamped by the store clock given at construction.
 */
public final class InMemoryStore implements Store, RangeStore, SlotLeaseStore {
    private final TimeSource storeClock;
    private final Map<String, RangeRow> ranges = new ConcurrentHashMap<>();
    private final Map<String, Namespace> namespaces = new ConcurrentHashMap<>();

    /**
     * A store on the system clock.
     */
    public InMemoryStore() {
        this(TimeSource.SYSTEM);
    }

    /**
     * A store on its own clock, for tests that move the store clock apart from the client clock, such as a
     * {@link ManualClock}.
     *
     * @param storeClock the store clock
     */
    public InMemoryStore(TimeSource storeClock) {
        this.storeClock = Objects.requireNonNull(storeClock, "storeClock");
    }

    /**
     * The clock that stamps every lease of this store.
     *
     * @return the store clock
     */
    public TimeSource storeClock() {
        return storeClock;
    }

    @Override
    public RangeStore ranges() {
        return this;
    }

    @Override
    public SlotLeaseStore slots() {
        return this;
    }

    @Override
    public Range reserve(String key, long size, long initialOffset) {
        Objects.requireNonNull(key, "key");
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive: " + size);
        }
        if (initialOffset < 0) {
            throw new IllegalArgumentException("initialOffset must not be negative: " + initialOffset);
        }
        Range[] reserved = new Range[1];
        ranges.compute(key, (k, row) -> {
            RangeRow current = row == null ? new RangeRow(initialOffset, false) : row;
            if (current.sealed()) {
                throw new RangeSealedException(key);
            }
            long start = current.next();
            if (start > Long.MAX_VALUE - size) {
                throw new RangeExhaustedException(key);
            }
            reserved[0] = new Range(start, start + size);
            return new RangeRow(start + size, false);
        });
        return reserved[0];
    }

    /**
     * Seals the key; a key that does not exist yet is sealed too, so it is never created (S6).
     *
     * @param key the range key
     * @return true
     */
    @Override
    public boolean seal(String key) {
        Objects.requireNonNull(key, "key");
        ranges.compute(key, (k, row) -> new RangeRow(row == null ? 0 : row.next(), true));
        return true;
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
        Namespace ns = namespaces.computeIfAbsent(namespace, k -> new Namespace(limit, toleranceMillis));
        if (ns.limit != limit || ns.toleranceMillis != toleranceMillis) {
            throw new NamespaceMismatchException(namespace, "first used with limit " + ns.limit + " and tolerance " + ns.toleranceMillis
                + "ms, now " + limit + " and " + toleranceMillis + "ms");
        }
        synchronized (ns) {
            long now = storeClock.currentMillis();
            boolean duplicate = false;
            for (SlotRow row : ns.rows) {
                duplicate |= !row.isFree(now) && row.owner.key().equals(owner.key()) && !row.owner.incarnation().equals(owner.incarnation());
            }
            // §2.1, each step a conditional update of one slot: held by this very owner, preferred, sticky, lowest.
            for (int slot = 0; slot < limit; slot++) {
                if (owner.equals(ns.rows[slot].owner)) {
                    return new AcquireResult.Acquired(ns.rows[slot].toLease(namespace, slot, now), AcquireReason.IDEMPOTENT, duplicate);
                }
            }
            if (preferredSlot.isPresent()) {
                int preferred = preferredSlot.getAsInt();
                if (preferred >= 0 && preferred < limit && ns.rows[preferred].isFree(now)) {
                    return take(ns, namespace, preferred, owner, now, ttlMillis, AcquireReason.PREFERRED, duplicate);
                }
            }
            for (int slot = 0; slot < limit; slot++) {
                SlotRow row = ns.rows[slot];
                if (owner.key().equals(row.lastOwnerKey) && row.isFree(now)) {
                    return take(ns, namespace, slot, owner, now, ttlMillis, AcquireReason.STICKY, duplicate);
                }
            }
            long earliestExpiresAt = Long.MAX_VALUE;
            for (int slot = 0; slot < limit; slot++) {
                SlotRow row = ns.rows[slot];
                if (row.isFree(now)) {
                    return take(ns, namespace, slot, owner, now, ttlMillis, AcquireReason.LOWEST, duplicate);
                }
                earliestExpiresAt = Math.min(earliestExpiresAt, row.expiresAt);
            }
            return new AcquireResult.Exhausted(earliestExpiresAt);
        }
    }

    private static AcquireResult take(Namespace ns, String namespace, int slot, Owner owner, long now, long ttlMillis,
                                      AcquireReason reason, boolean duplicateOwnerKey) {
        SlotRow row = ns.rows[slot];
        row.fence++;
        row.owner = owner;
        row.lastOwnerKey = owner.key();
        row.floor = row.nextFloor;
        row.expiresAt = now + ttlMillis;
        row.ceiling = Math.max(row.expiresAt + ns.toleranceMillis, row.floor);
        row.nextFloor = row.ceiling;
        return new AcquireResult.Acquired(row.toLease(namespace, slot, now), reason, duplicateOwnerKey);
    }

    @Override
    public Optional<SlotLease> renew(SlotLease lease, Duration ttl, Duration tolerance) {
        Objects.requireNonNull(lease, "lease");
        long ttlMillis = positiveMillis(ttl, "ttl");
        long toleranceMillis = toleranceMillis(tolerance);
        Namespace ns = namespaces.get(lease.namespace());
        if (ns == null || lease.slot() >= ns.limit) {
            return Optional.empty();
        }
        synchronized (ns) {
            SlotRow row = ns.rows[lease.slot()];
            if (!row.isHeldBy(lease)) {
                return Optional.empty();
            }
            long now = storeClock.currentMillis();
            row.expiresAt = Math.max(row.expiresAt, now + ttlMillis);
            row.ceiling = Math.max(row.ceiling, row.expiresAt + toleranceMillis);
            row.nextFloor = row.ceiling;
            return Optional.of(row.toLease(lease.namespace(), lease.slot(), now));
        }
    }

    @Override
    public boolean release(SlotLease lease, long lastIssued) {
        Objects.requireNonNull(lease, "lease");
        Namespace ns = namespaces.get(lease.namespace());
        if (ns == null || lease.slot() >= ns.limit) {
            return false;
        }
        synchronized (ns) {
            SlotRow row = ns.rows[lease.slot()];
            if (!row.isHeldBy(lease)) {
                return false;
            }
            row.owner = null;
            row.expiresAt = storeClock.currentMillis();
            row.nextFloor = Math.max(row.floor, Math.min(lastIssued, row.ceiling));
            row.fence++;
            return true;
        }
    }

    @Override
    public void close() {
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

    private record RangeRow(long next, boolean sealed) {
    }

    private static final class Namespace {
        private final int limit;
        private final long toleranceMillis;
        private final SlotRow[] rows;

        private Namespace(int limit, long toleranceMillis) {
            this.limit = limit;
            this.toleranceMillis = toleranceMillis;
            this.rows = new SlotRow[limit];
            for (int slot = 0; slot < limit; slot++) {
                rows[slot] = new SlotRow();
            }
        }
    }

    /**
     * One slot as a store keeps it (05 §2): guarded by its namespace.
     */
    private static final class SlotRow {
        private Owner owner;
        private String lastOwnerKey;
        private long fence;
        private long expiresAt;
        private long floor;
        private long ceiling;
        private long nextFloor;

        private boolean isFree(long now) {
            return owner == null || expiresAt < now;
        }

        private boolean isHeldBy(SlotLease lease) {
            return owner != null && owner.incarnation().equals(lease.owner().incarnation()) && fence == lease.fence();
        }

        private SlotLease toLease(String namespace, int slot, long now) {
            return new SlotLease(namespace, slot, owner, fence, expiresAt, floor, ceiling, now);
        }
    }
}
