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

package me.ahoo.cosid.store.redis;

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

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The Redis store (06-stores.md §2.2): both primitives as Lua scripts run through {@link RedisScripts}, stamped by the
 * Redis clock ({@code TIME}). Redis 7.0 or later (D27).
 *
 * <p>Keys, each group sharing one hash tag so that a script touches one Redis Cluster slot:
 * <ul>
 *   <li>{@code cosid:{key}}: the counter of a range key, a decimal string; {@code cosid:{key}:sealed}: its seal marker.
 *       Keys are never deleted (D23).</li>
 *   <li>{@code cosid:{ns}:meta}: the limit, tolerance and slot offset a namespace was first used with (a hash of
 *       {@code limit}, {@code tolerance}, {@code slotOffset}; a meta written by 5.0.0-alpha.1 has no
 *       {@code slotOffset}, which reads as 0);
 *       {@code cosid:{ns}:slot}: slot to its row; {@code cosid:{ns}:free}: the free slots, scored by slot number;
 *       {@code cosid:{ns}:live}: the live leases, scored by {@code expiresAt}; {@code cosid:{ns}:owner}: owner key to
 *       the slot it took last; {@code cosid:{ns}:held}: owner key to the slots whose row names it.</li>
 * </ul>
 * Every acquire is a few hash and sorted set lookups, O(log n) in the slot count, with a sweep of at most
 * {@code sweepBatch} expired leases; there is no scan of the namespace.
 *
 * <p><strong>Durability precondition.</strong> The uniqueness of the ids rests on "an acknowledged write is never
 * lost": either a single node with AOF and {@code appendfsync always}, or {@code WAIT} covering every replica. Under
 * asynchronous replication a failover can lose range and lease writes and so produce duplicate ids; where uniqueness
 * is strict, prefer the JDBC or ZooKeeper store. The store only hands out a range or a ceiling once its write is
 * acknowledged ({@link RedisScripts}).
 */
public final class RedisStore implements Store, RangeStore, SlotLeaseStore {
    /** How many expired leases one acquire moves to the free set at most. */
    public static final int DEFAULT_SWEEP_BATCH = 16;
    private static final String PREFIX = "cosid:{";

    private final RedisScripts scripts;
    private final int sweepBatch;
    private final AutoCloseable resources;

    /**
     * A store on the given scripts, with the default sweep batch; closing it closes nothing.
     *
     * @param scripts how the store runs its scripts
     */
    public RedisStore(RedisScripts scripts) {
        this(scripts, DEFAULT_SWEEP_BATCH, () -> {
        });
    }

    /**
     * A store on the given scripts.
     *
     * @param scripts    how the store runs its scripts
     * @param sweepBatch how many expired leases one acquire moves to the free set at most, positive
     * @param resources  closed with the store: what the store created itself, never an injected client
     */
    public RedisStore(RedisScripts scripts, int sweepBatch, AutoCloseable resources) {
        this.scripts = Objects.requireNonNull(scripts, "scripts");
        if (sweepBatch < 1) {
            throw new IllegalArgumentException("sweepBatch must be positive: " + sweepBatch);
        }
        this.sweepBatch = sweepBatch;
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @Override
    public RangeStore ranges() {
        return this;
    }

    @Override
    public SlotLeaseStore slots() {
        return this;
    }

    static String rangeKey(String key) {
        return PREFIX + key + "}";
    }

    static String sealedKey(String key) {
        return PREFIX + key + "}:sealed";
    }

    static String namespaceKey(String namespace, String suffix) {
        return PREFIX + namespace + "}:" + suffix;
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
        List<String> reply = Script.RESERVE.eval(scripts, List.of(rangeKey(key), sealedKey(key)),
            List.of(Long.toString(size), Long.toString(initialOffset)));
        return switch (reply.get(0)) {
            case "reserved" -> {
                long end = Long.parseLong(reply.get(1));
                yield new Range(end - size, end);
            }
            case "sealed" -> throw new RangeSealedException(key);
            case "exhausted" -> throw new RangeExhaustedException(key);
            default -> throw unexpected("reserve", reply);
        };
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
        Script.SEAL.eval(scripts, List.of(sealedKey(key)), List.of());
        return true;
    }

    @Override
    public AcquireResult acquire(String namespace, Owner owner, int limit, Duration ttl, Duration tolerance, int slotOffset, OptionalInt preferredSlot) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(preferredSlot, "preferredSlot");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive: " + limit);
        }
        if (slotOffset < 0) {
            throw new IllegalArgumentException("slotOffset must not be negative: " + slotOffset);
        }
        long ttlMillis = positiveMillis(ttl, "ttl");
        long toleranceMillis = toleranceMillis(tolerance);
        List<String> keys = List.of(namespaceKey(namespace, "meta"), namespaceKey(namespace, "slot"), namespaceKey(namespace, "free"),
            namespaceKey(namespace, "live"), namespaceKey(namespace, "owner"), namespaceKey(namespace, "held"));
        List<String> args = List.of(Integer.toString(limit), Long.toString(toleranceMillis), Long.toString(ttlMillis), owner.key(),
            owner.incarnation().toString(), preferredSlot.isPresent() ? Integer.toString(preferredSlot.getAsInt()) : "",
            Integer.toString(sweepBatch), Integer.toString(slotOffset));
        List<String> reply = Script.ACQUIRE.eval(scripts, keys, args);
        return switch (reply.get(0)) {
            case "acquired" -> {
                SlotLease lease = SlotLease.builder()
                    .namespace(namespace)
                    .slot(Integer.parseInt(reply.get(3)))
                    .owner(owner)
                    .fence(Long.parseLong(reply.get(4)))
                    .expiresAt(Long.parseLong(reply.get(5)))
                    .floor(Long.parseLong(reply.get(6)))
                    .ceiling(Long.parseLong(reply.get(7)))
                    .storeNow(Long.parseLong(reply.get(8)))
                    .build();
                yield AcquireResult.Acquired.builder(lease, AcquireReason.valueOf(reply.get(1)))
                    .duplicateOwnerKey("1".equals(reply.get(2)))
                    .build();
            }
            case "exhausted" -> new AcquireResult.Exhausted(Long.parseLong(reply.get(1)));
            case "mismatch" -> throw new NamespaceMismatchException(namespace, "first used with limit " + reply.get(1) + ", tolerance "
                + reply.get(2) + "ms and slot offset " + reply.get(3) + ", now " + limit + ", " + toleranceMillis + "ms and " + slotOffset);
            default -> throw unexpected("acquire", reply);
        };
    }

    @Override
    public Optional<SlotLease> renew(SlotLease lease, Duration ttl, Duration tolerance) {
        Objects.requireNonNull(lease, "lease");
        long ttlMillis = positiveMillis(ttl, "ttl");
        long toleranceMillis = toleranceMillis(tolerance);
        String namespace = lease.namespace();
        List<String> reply = Script.RENEW.eval(scripts,
            List.of(namespaceKey(namespace, "slot"), namespaceKey(namespace, "free"), namespaceKey(namespace, "live")),
            List.of(Integer.toString(lease.slot()), lease.owner().incarnation().toString(), Long.toString(lease.fence()),
                Long.toString(ttlMillis), Long.toString(toleranceMillis)));
        return switch (reply.get(0)) {
            case "renewed" -> Optional.of(SlotLease.builder()
                .namespace(namespace)
                .slot(lease.slot())
                .owner(lease.owner())
                .fence(Long.parseLong(reply.get(1)))
                .expiresAt(Long.parseLong(reply.get(2)))
                .floor(Long.parseLong(reply.get(3)))
                .ceiling(Long.parseLong(reply.get(4)))
                .storeNow(Long.parseLong(reply.get(5)))
                .build());
            case "lost" -> Optional.empty();
            default -> throw unexpected("renew", reply);
        };
    }

    @Override
    public boolean release(SlotLease lease, long lastIssued) {
        Objects.requireNonNull(lease, "lease");
        String namespace = lease.namespace();
        List<String> reply = Script.RELEASE.eval(scripts,
            List.of(namespaceKey(namespace, "slot"), namespaceKey(namespace, "free"), namespaceKey(namespace, "live"),
                namespaceKey(namespace, "held")),
            List.of(Integer.toString(lease.slot()), lease.owner().incarnation().toString(), Long.toString(lease.fence()),
                Long.toString(lastIssued)));
        return switch (reply.get(0)) {
            case "released" -> true;
            case "lost" -> false;
            default -> throw unexpected("release", reply);
        };
    }

    @Override
    public void close() {
        try {
            resources.close();
        } catch (Exception e) {
            throw new RedisStoreException("Closing the Redis store failed", e);
        }
    }

    private static RedisStoreException unexpected(String operation, List<String> reply) {
        return new RedisStoreException("Unexpected reply of " + operation + ": " + reply);
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
}
