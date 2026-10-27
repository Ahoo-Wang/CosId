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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.NamespaceMismatchException;
import me.ahoo.cosid.RangeExhaustedException;
import me.ahoo.cosid.RangeSealedException;
import me.ahoo.cosid.lease.AcquireReason;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.store.Store;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * The edge cases of the Lua scripts on standalone Redis, read back from the keys of 06-stores.md §2.2.
 */
class RedisScriptEdgeTest {
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final Duration TOLERANCE = Duration.ofSeconds(1);
    private static StatefulRedisConnection<String, String> connection;
    private static RedisCommands<String, String> redis;

    @BeforeAll
    static void connect() {
        connection = RedisServers.standalone().connect();
        redis = connection.sync();
    }

    @AfterAll
    static void disconnect() {
        connection.close();
    }

    private static Store open(Map<String, Object> options) {
        return RedisServers.open(RedisServers.standalone(), options);
    }

    private static String newKey() {
        return "edge-" + UUID.randomUUID();
    }

    private static AcquireResult.Acquired acquired(Store store, String namespace, Owner owner, int limit, Duration ttl, OptionalInt preferred) {
        return assertInstanceOf(AcquireResult.Acquired.class, store.slots().acquire(namespace, owner, limit, ttl, TOLERANCE, preferred));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    @Test
    void reserveUpToLongMaxValueStaysExactThenExhausts() {
        String key = newKey();
        try (Store store = open(Map.of())) {
            Range last = store.ranges().reserve(key, 10, Long.MAX_VALUE - 10);
            assertEquals(new Range(Long.MAX_VALUE - 10, Long.MAX_VALUE), last);
            assertEquals(Long.toString(Long.MAX_VALUE), redis.get(RedisStore.rangeKey(key)), "the counter is a decimal string, exact");
            assertThrows(RangeExhaustedException.class, () -> store.ranges().reserve(key, 1, 0));
            assertEquals(Long.toString(Long.MAX_VALUE), redis.get(RedisStore.rangeKey(key)), "an overflow leaves the counter alone");
            String odd = newKey();
            Range first = store.ranges().reserve(odd, 3, (1L << 60) + 1);
            assertEquals(new Range((1L << 60) + 1, (1L << 60) + 4), first, "values that are no doubles stay exact");
        }
    }

    @Test
    void sealKeepsTheCounterAndNeverCreatesAKey() {
        String used = newKey();
        String unused = newKey();
        try (Store store = open(Map.of())) {
            store.ranges().reserve(used, 100, 7);
            assertTrue(store.ranges().seal(used));
            assertTrue(store.ranges().seal(unused));
            assertThrows(RangeSealedException.class, () -> store.ranges().reserve(used, 1, 0));
            assertThrows(RangeSealedException.class, () -> store.ranges().reserve(unused, 1, 0));
            assertEquals("107", redis.get(RedisStore.rangeKey(used)), "the sealed counter is kept (D23)");
            assertEquals(0L, redis.exists(RedisStore.rangeKey(unused)), "a key sealed before its first reserve is never created (S6)");
            assertEquals("1", redis.get(RedisStore.sealedKey(unused)));
        }
    }

    @Test
    void scriptsReloadAfterAFlush() {
        try (Store store = open(Map.of())) {
            String key = newKey();
            store.ranges().reserve(key, 1, 0);
            redis.scriptFlush();
            assertEquals(new Range(1, 2), store.ranges().reserve(key, 1, 0), "NOSCRIPT falls back to EVAL");
        }
    }

    @Test
    void namespaceKeepsItsFirstLimitAndTolerance() {
        String namespace = newKey();
        try (Store store = open(Map.of())) {
            acquired(store, namespace, new Owner("host-a", UUID.randomUUID()), 8, TTL, OptionalInt.empty());
            assertEquals(Map.of("limit", "8", "tolerance", "1000"), redis.hgetall(RedisStore.namespaceKey(namespace, "meta")));
            NamespaceMismatchException grown = assertThrows(NamespaceMismatchException.class,
                () -> store.slots().acquire(namespace, new Owner("host-b", UUID.randomUUID()), 16, TTL, TOLERANCE, OptionalInt.empty()));
            assertTrue(grown.getMessage().contains("limit 8 and tolerance 1000ms, now 16 and 1000ms"), grown.getMessage());
            assertThrows(NamespaceMismatchException.class,
                () -> store.slots().acquire(namespace, new Owner("host-b", UUID.randomUUID()), 4, TTL, TOLERANCE, OptionalInt.empty()));
            assertEquals(7L, redis.zcard(RedisStore.namespaceKey(namespace, "free")), "a rejected acquire changes nothing");
        }
    }

    @Test
    void firstAcquirePrefillsEveryFreeSlotInBatches() {
        String namespace = newKey();
        int limit = 1 << 12;
        try (Store store = open(Map.of())) {
            acquired(store, namespace, new Owner("host-a", UUID.randomUUID()), limit, TTL, OptionalInt.of(limit - 1));
            assertEquals(limit - 1, redis.zcard(RedisStore.namespaceKey(namespace, "free")));
            assertEquals(List.of("0"), redis.zrange(RedisStore.namespaceKey(namespace, "free"), 0, 0));
            assertEquals(List.of(Integer.toString(limit - 2)), redis.zrange(RedisStore.namespaceKey(namespace, "free"), -1, -1));
            assertEquals(List.of(Integer.toString(limit - 1)), redis.zrange(RedisStore.namespaceKey(namespace, "live"), 0, -1));
        }
    }

    @Test
    void sweepMovesAtMostOneBatchOfExpiredLeases() {
        String namespace = newKey();
        try (Store store = open(Map.of("sweepBatch", 2))) {
            for (int i = 0; i < 6; i++) {
                acquired(store, namespace, new Owner("host-" + i, UUID.randomUUID()), 8, Duration.ofMillis(50), OptionalInt.empty());
            }
            acquired(store, namespace, new Owner("host-6", UUID.randomUUID()), 8, TTL, OptionalInt.empty());
            sleep(150);
            assertEquals(1L, redis.zcard(RedisStore.namespaceKey(namespace, "free")));
            SlotLease taken = acquired(store, namespace, new Owner("host-new", UUID.randomUUID()), 8, TTL, OptionalInt.empty()).lease();
            assertEquals(0, taken.slot(), "the sweep made slot 0 free, the lowest one");
            assertEquals(2L, redis.zcard(RedisStore.namespaceKey(namespace, "free")), "two swept, one of them taken, slot 7 still free");
            assertEquals(6L, redis.zcard(RedisStore.namespaceKey(namespace, "live")), "four expired leases wait for later sweeps");
        }
    }

    @Test
    void renewOfASweptLeaseMakesItLiveAgain() {
        String namespace = newKey();
        Owner owner = new Owner("host-a", UUID.randomUUID());
        try (Store store = open(Map.of())) {
            SlotLease lease = acquired(store, namespace, owner, 2, Duration.ofMillis(50), OptionalInt.empty()).lease();
            sleep(150);
            AcquireResult.Acquired other = acquired(store, namespace, new Owner("host-b", UUID.randomUUID()), 2, TTL, OptionalInt.of(1));
            assertEquals(AcquireReason.PREFERRED, other.reason());
            assertEquals(List.of(Integer.toString(lease.slot())), redis.zrange(RedisStore.namespaceKey(namespace, "free"), 0, -1),
                "the expired lease was swept");
            SlotLease renewed = store.slots().renew(lease, TTL, TOLERANCE).orElseThrow();
            assertEquals(lease.fence(), renewed.fence(), "nobody took it over");
            assertEquals(0L, redis.zcard(RedisStore.namespaceKey(namespace, "free")));
            assertInstanceOf(AcquireResult.Exhausted.class,
                store.slots().acquire(namespace, new Owner("host-c", UUID.randomUUID()), 2, TTL, TOLERANCE, OptionalInt.empty()));
        }
    }

    @Test
    void ownerKeysWithSeparatorsRoundTrip() {
        String namespace = newKey();
        String key = "pod|a\\b|\\p";
        try (Store store = open(Map.of())) {
            acquired(store, namespace, new Owner("host-0", UUID.randomUUID()), 4, TTL, OptionalInt.empty());
            SlotLease first = acquired(store, namespace, new Owner(key, UUID.randomUUID()), 4, TTL, OptionalInt.empty()).lease();
            AcquireResult.Acquired duplicate = acquired(store, namespace, new Owner(key, UUID.randomUUID()), 4, TTL, OptionalInt.empty());
            assertTrue(duplicate.duplicateOwnerKey());
            assertTrue(store.slots().release(duplicate.lease(), duplicate.lease().ceiling()));
            assertTrue(store.slots().release(first, first.ceiling()));
            AcquireResult.Acquired again = acquired(store, namespace, new Owner(key, UUID.randomUUID()), 4, TTL, OptionalInt.empty());
            assertEquals(AcquireReason.STICKY, again.reason());
            assertFalse(again.duplicateOwnerKey());
            assertEquals(Integer.toString(again.lease().slot()), redis.hget(RedisStore.namespaceKey(namespace, "held"), key));
            String row = redis.hget(RedisStore.namespaceKey(namespace, "slot"), Integer.toString(again.lease().slot()));
            assertEquals(8, row.split("\\|", -1).length, "owner keys are escaped in the row: " + row);
        }
    }

    @Test
    void releaseClampsLastIssuedOfAnyLong() {
        String namespace = newKey();
        try (Store store = open(Map.of())) {
            SlotLease a = acquired(store, namespace, new Owner("host-a", UUID.randomUUID()), 1, TTL, OptionalInt.empty()).lease();
            assertTrue(store.slots().release(a, Long.MAX_VALUE));
            SlotLease b = acquired(store, namespace, new Owner("host-b", UUID.randomUUID()), 1, TTL, OptionalInt.empty()).lease();
            assertEquals(a.ceiling(), b.floor());
            assertTrue(store.slots().release(b, Long.MIN_VALUE));
            SlotLease c = acquired(store, namespace, new Owner("host-c", UUID.randomUUID()), 1, TTL, OptionalInt.empty()).lease();
            assertEquals(b.floor(), c.floor());
            assertFalse(store.slots().release(b, 0), "a stale fence");
            assertTrue(store.slots().renew(new SlotLease("missing-" + namespace, 0, c.owner(), c.fence(), 0, 0, 0, 0), TTL, TOLERANCE).isEmpty());
            assertFalse(store.slots().release(new SlotLease("missing-" + namespace, 0, c.owner(), c.fence(), 0, 0, 0, 0), 0));
        }
    }
}
