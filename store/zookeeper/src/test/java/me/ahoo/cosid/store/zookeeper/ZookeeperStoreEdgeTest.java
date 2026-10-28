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

package me.ahoo.cosid.store.zookeeper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.NamespaceMismatchException;
import me.ahoo.cosid.RangeSealedException;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.store.StoreTimeoutException;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.zookeeper.KeeperException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The edges of the ZooKeeper store that the contracts do not reach: argument checks, nodes left by other writers
 * (parents without data, unreadable data), and the failures of the Curator calls.
 */
class ZookeeperStoreEdgeTest {
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final Duration TOLERANCE = Duration.ofSeconds(1);
    private static CuratorFramework client;
    private static ZookeeperStore store;

    @BeforeAll
    static void open() {
        client = ZookeeperTests.newClient();
        store = new ZookeeperStore(client, ZookeeperTests.TIMEOUT);
    }

    @AfterAll
    static void close() {
        store.close();
        client.close();
    }

    private static Owner owner(String key) {
        return new Owner(key, UUID.randomUUID());
    }

    @Test
    void rangeArgumentsAreChecked() {
        assertThrows(IllegalArgumentException.class, () -> store.ranges().reserve("edge/a", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> store.ranges().reserve("edge/a", 1, -1));
        assertThrows(NullPointerException.class, () -> store.ranges().reserve(null, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> store.ranges().seal("bad//key"));
    }

    @Test
    void rangePathsAreValidatedOncePerKeyUpToACap() {
        ZookeeperRangeStore ranges = new ZookeeperRangeStore(new Zk(client, ZookeeperTests.TIMEOUT));
        assertSame(ranges.cachedPath("edge/cached"), ranges.cachedPath("edge/cached"));
        for (int i = 0; i < ZookeeperRangeStore.MAX_CACHED_PATHS + 10; i++) {
            assertEquals(ZookeeperRangeStore.ROOT + "/many/" + i, ranges.cachedPath("many/" + i));
        }
        assertThrows(IllegalArgumentException.class, () -> ranges.cachedPath("bad//key"));
        assertThrows(NullPointerException.class, () -> ranges.cachedPath(null));
    }

    @Test
    void aParentKeyAndItsPeriodsAreIndependentKeys() {
        String key = "edge/" + UUID.randomUUID();
        assertEquals(new Range(7, 17), store.ranges().reserve(key + "/2026", 10, 7), "creates the parent without data");
        assertEquals(new Range(3, 8), store.ranges().reserve(key, 5, 3), "a parent without data is a new key");
        assertEquals(new Range(8, 9), store.ranges().reserve(key, 1, 3));
        assertEquals(new Range(17, 18), store.ranges().reserve(key + "/2026", 1, 7));
    }

    @Test
    void sealingCoversParentsUnusedAndSealedKeys() {
        String key = "edge/" + UUID.randomUUID();
        store.ranges().reserve(key + "/2026", 1, 0);
        assertTrue(store.ranges().seal(key), "a parent without data is sealed in place");
        assertThrows(RangeSealedException.class, () -> store.ranges().reserve(key, 1, 0));
        assertTrue(store.ranges().seal(key), "sealing twice");
        assertEquals(new Range(1, 2), store.ranges().reserve(key + "/2026", 1, 0), "its periods stay usable");
        String unused = "edge/" + UUID.randomUUID();
        assertTrue(store.ranges().seal(unused));
        assertThrows(RangeSealedException.class, () -> store.ranges().reserve(unused, 1, 0));
    }

    @Test
    void unreadableRangeDataFails() throws Exception {
        String key = "edge/" + UUID.randomUUID();
        client.create().creatingParentsIfNeeded().forPath(ZookeeperRangeStore.path(key), "garbage".getBytes(StandardCharsets.US_ASCII));
        assertThrows(CosIdException.class, () -> store.ranges().reserve(key, 1, 0));
        assertThrows(CosIdException.class, () -> store.ranges().seal(key));
    }

    @Test
    void slotArgumentsAreChecked() {
        String namespace = "edge-" + UUID.randomUUID();
        Owner owner = owner("host-a");
        assertThrows(IllegalArgumentException.class, () -> store.slots().acquire(namespace, owner, 0, TTL, TOLERANCE, 0, OptionalInt.empty()));
        assertThrows(IllegalArgumentException.class, () -> store.slots().acquire(namespace, owner, 1, Duration.ZERO, TOLERANCE, 0, OptionalInt.empty()));
        assertThrows(IllegalArgumentException.class, () -> store.slots().acquire(namespace, owner, 1, TTL.negated(), TOLERANCE, 0, OptionalInt.empty()));
        assertThrows(IllegalArgumentException.class, () -> store.slots().acquire(namespace, owner, 1, TTL, TOLERANCE.negated(), 0, OptionalInt.empty()));
        assertThrows(IllegalArgumentException.class, () -> store.slots().acquire("..", owner, 1, TTL, TOLERANCE, 0, OptionalInt.empty()));
        assertThrows(NullPointerException.class, () -> store.slots().acquire(namespace, null, 1, TTL, TOLERANCE, 0, OptionalInt.empty()));
        assertThrows(NullPointerException.class, () -> store.slots().renew(null, TTL, TOLERANCE));
        assertThrows(NullPointerException.class, () -> store.slots().release(null, 0));
    }

    @Test
    void leasesOfMissingSlotsAreNotHeld() {
        String namespace = "edge-" + UUID.randomUUID();
        SlotLease lease = assertInstanceOf(AcquireResult.Acquired.class,
            store.slots().acquire(namespace, owner("host-a"), 4, TTL, TOLERANCE, 0, OptionalInt.empty())).lease();
        SlotLease elsewhere = SlotLease.builder()
            .namespace(namespace)
            .slot(3)
            .owner(lease.owner())
            .fence(lease.fence())
            .expiresAt(lease.expiresAt())
            .floor(lease.floor())
            .ceiling(lease.ceiling())
            .storeNow(lease.storeNow())
            .build();
        assertEquals(Optional.empty(), store.slots().renew(elsewhere, TTL, TOLERANCE));
        assertFalse(store.slots().release(elsewhere, 0));
        SlotLease unknown = SlotLease.builder()
            .namespace("edge-" + UUID.randomUUID())
            .slot(0)
            .owner(lease.owner())
            .fence(1)
            .expiresAt(0)
            .floor(0)
            .ceiling(0)
            .storeNow(0)
            .build();
        assertFalse(store.slots().release(unknown, 0));
        assertTrue(store.slots().release(lease, lease.ceiling()));
        assertFalse(store.slots().release(lease, lease.ceiling()), "a second release finds the slot free");
    }

    @Test
    void preferredSlotOutsideTheLimitIsIgnored() {
        String namespace = "edge-" + UUID.randomUUID();
        AcquireResult.Acquired result = assertInstanceOf(AcquireResult.Acquired.class,
            store.slots().acquire(namespace, owner("host-a"), 2, TTL, TOLERANCE, 0, OptionalInt.of(9)));
        assertEquals(0, result.lease().slot());
        assertInstanceOf(AcquireResult.Acquired.class, store.slots().acquire(namespace, owner("host-b"), 2, TTL, TOLERANCE, 0, OptionalInt.of(-1)));
    }

    @Test
    void namespaceNodeWithoutDataGetsTheMetadata() throws Exception {
        String namespace = "edge-" + UUID.randomUUID();
        client.create().creatingParentsIfNeeded().forPath(ZookeeperSlotLeaseStore.namespacePath(namespace), new byte[0]);
        assertInstanceOf(AcquireResult.Acquired.class, store.slots().acquire(namespace, owner("host-a"), 2, TTL, TOLERANCE, 0, OptionalInt.empty()));
        ZookeeperStore other = new ZookeeperStore(client, ZookeeperTests.TIMEOUT);
        assertThrows(NamespaceMismatchException.class, () -> other.slots().acquire(namespace, owner("host-b"), 4, TTL, TOLERANCE, 0, OptionalInt.empty()),
            "another instance reads the recorded metadata");
    }

    @Test
    void alpha1MetadataWithoutSlotOffsetHasOffsetZero() throws Exception {
        String namespace = "edge-" + UUID.randomUUID();
        byte[] alpha1 = ("limit=2\ntolerance=" + TOLERANCE.toMillis() + "\n").getBytes(StandardCharsets.US_ASCII);
        client.create().creatingParentsIfNeeded().forPath(ZookeeperSlotLeaseStore.namespacePath(namespace), alpha1);
        assertInstanceOf(AcquireResult.Acquired.class, store.slots().acquire(namespace, owner("host-a"), 2, TTL, TOLERANCE, 0, OptionalInt.empty()));
        ZookeeperStore other = new ZookeeperStore(client, ZookeeperTests.TIMEOUT);
        assertThrows(NamespaceMismatchException.class, () -> other.slots().acquire(namespace, owner("host-b"), 2, TTL, TOLERANCE, 1, OptionalInt.empty()),
            "a node without the slotOffset line has offset 0");
        assertArrayEquals(alpha1, client.getData().forPath(ZookeeperSlotLeaseStore.namespacePath(namespace)), "the recorded metadata is not rewritten");
    }

    @Test
    void negativeSlotOffsetIsRejected() {
        String namespace = "edge-" + UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> store.slots().acquire(namespace, owner("host-a"), 2, TTL, TOLERANCE, -1, OptionalInt.empty()));
    }

    @Test
    void unreadableNodesFail() throws Exception {
        String garbled = "edge-" + UUID.randomUUID();
        client.create().creatingParentsIfNeeded().forPath(ZookeeperSlotLeaseStore.namespacePath(garbled), "limit=x".getBytes(StandardCharsets.US_ASCII));
        assertThrows(CosIdException.class, () -> store.slots().acquire(garbled, owner("host-a"), 2, TTL, TOLERANCE, 0, OptionalInt.empty()));
        String partial = "edge-" + UUID.randomUUID();
        client.create().creatingParentsIfNeeded().forPath(ZookeeperSlotLeaseStore.namespacePath(partial), "limit=2\n".getBytes(StandardCharsets.US_ASCII));
        assertThrows(CosIdException.class, () -> store.slots().acquire(partial, owner("host-a"), 2, TTL, TOLERANCE, 0, OptionalInt.empty()));
        String badSlot = "edge-" + UUID.randomUUID();
        store.slots().acquire(badSlot, owner("host-a"), 2, TTL, TOLERANCE, 0, OptionalInt.empty());
        client.setData().forPath(ZookeeperSlotLeaseStore.slotPath(badSlot, 0), "fence=1\n".getBytes(StandardCharsets.UTF_8));
        assertThrows(CosIdException.class, () -> store.slots().acquire(badSlot, owner("host-b"), 2, TTL, TOLERANCE, 0, OptionalInt.empty()));
        client.create().forPath(ZookeeperSlotLeaseStore.namespacePath(badSlot) + "/other");
        client.create().forPath(ZookeeperSlotLeaseStore.namespacePath(badSlot) + "/1234567890");
        client.setData().forPath(ZookeeperSlotLeaseStore.slotPath(badSlot, 0),
            SlotState.NEW.take(owner("host-c"), 0, TTL.toMillis(), TOLERANCE.toMillis()).bytes());
        assertEquals(1, assertInstanceOf(AcquireResult.Acquired.class,
            store.slots().acquire(badSlot, owner("host-d"), 2, TTL, TOLERANCE, 0, OptionalInt.empty())).lease().slot(), "other children are not slots");
    }

    @Test
    void curatorCallFailuresBecomeStoreFailures() {
        Zk zk = new Zk(client, Duration.ofMillis(200));
        assertThrows(CosIdException.class, () -> zk.call("throws", zk.deadline(), callback -> {
            throw new IllegalStateException("cannot start");
        }));
        assertThrows(CosIdException.class, () -> zk.call("never completes", zk.deadline(), callback -> {
        }));
        Thread.currentThread().interrupt();
        try {
            assertThrows(CosIdException.class, () -> zk.await("interrupted", zk.deadline(), new CompletableFuture<>()));
            assertTrue(Thread.interrupted(), "the interrupt is kept");
        } finally {
            Thread.interrupted();
        }
        CuratorEvent missing = zk.call("missing", zk.deadline(), callback -> client.getData().inBackground(callback).forPath("/cosid/edge/" + UUID.randomUUID()));
        assertEquals(KeeperException.Code.NONODE, Zk.code(missing));
        assertInstanceOf(KeeperException.NoNodeException.class, Zk.unexpected("missing", missing).getCause());
        assertThrows(CosIdException.class, () -> Zk.stat("missing", missing));
    }

    @Test
    void backoffPausesAndGivesUpAtTheDeadline() {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        for (int attempt = 0; attempt < 8; attempt++) {
            Zk.backoff(attempt, deadline, "contended");
        }
        // Out of time while contending is a store timeout (README, 06 §1), as a timed out call is.
        StoreTimeoutException timeout = assertThrows(StoreTimeoutException.class, () -> Zk.backoff(3, System.nanoTime() - 1, "contended"));
        assertTrue(timeout.getMessage().contains("after 4 conflicts"), timeout.getMessage());
        assertThrows(StoreTimeoutException.class, () -> Zk.backoff(0, System.nanoTime() - 1, "contended"));
        // An interrupted thread stops retrying at every attempt, whatever random pause it would have drawn (0 included).
        for (int attempt = 0; attempt < 8; attempt++) {
            int interruptedAttempt = attempt;
            Thread.currentThread().interrupt();
            try {
                CosIdException interrupted = assertThrows(CosIdException.class,
                    () -> Zk.backoff(interruptedAttempt, System.nanoTime() + Duration.ofSeconds(5).toNanos(), "interrupted"));
                assertFalse(StoreTimeoutException.isTimeout(interrupted), "an interruption is no timeout");
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void providerRejectsBadIntegers() {
        Map<String, Object> options = Map.of("connectString", ZookeeperTests.connectString(), "retry.maxRetries", "many");
        assertThrows(IllegalArgumentException.class, () -> new ZookeeperStoreProvider().open(
            me.ahoo.cosid.store.StoreConfig.builder("zk", "zookeeper").timeout(TTL).options(options).build()));
        Map<String, Object> numeric = Map.of("connectString", ZookeeperTests.connectString(), "retry.maxRetries", 2);
        new ZookeeperStoreProvider().open(me.ahoo.cosid.store.StoreConfig.builder("zk", "zookeeper").timeout(TTL).options(numeric).build()).close();
    }
}
