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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.lease.SlotLeaseStore;

import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.data.Stat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * What the slot nodes hold (06-stores.md §2.4): the fence in the data, so recreating a node does not reset it, and the
 * {@code mtime} of each write as the store clock.
 */
class ZookeeperSlotNodeTest {
    private static final Duration TOLERANCE = Duration.ofSeconds(1);
    private static final Duration TTL = Duration.ofSeconds(60);
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

    private static String newNamespace() {
        return "node-" + UUID.randomUUID();
    }

    private static SlotLease acquire(SlotLeaseStore slots, String namespace, String key, Duration ttl) {
        AcquireResult result = slots.acquire(namespace, new Owner(key, UUID.randomUUID()), 1, ttl, TOLERANCE, OptionalInt.empty());
        return assertInstanceOf(AcquireResult.Acquired.class, result).lease();
    }

    @Test
    void recreatedNodeKeepsItsFence() throws Exception {
        String namespace = newNamespace();
        SlotLeaseStore slots = store.slots();
        SlotLease a = acquire(slots, namespace, "host-a", TTL);
        for (int i = 0; i < 4; i++) {
            a = slots.renew(a, TTL, TOLERANCE).orElseThrow();
        }
        String path = ZookeeperSlotLeaseStore.slotPath(namespace, 0);
        Stat before = new Stat();
        byte[] data = client.getData().storingStatIn(before).forPath(path);
        assertEquals(4, before.getVersion());
        // An operator restores the node with its data: its version starts again at 0.
        client.delete().forPath(path);
        client.create().forPath(path, data);
        assertEquals(0, client.checkExists().forPath(path).getVersion());

        SlotLease renewed = slots.renew(a, TTL, TOLERANCE).orElseThrow(() -> new AssertionError("the holder keeps the restored node"));
        assertEquals(a.fence(), renewed.fence());
        assertTrue(slots.release(renewed, renewed.ceiling()));
        SlotLease b = acquire(slots, namespace, "host-b", TTL);
        assertEquals(a.fence() + 2, b.fence(), "release and acquire raise the fence of the data, from where it was");
        assertTrue(b.floor() >= renewed.ceiling());
        b = slots.renew(slots.renew(b, TTL, TOLERANCE).orElseThrow(), TTL, TOLERANCE).orElseThrow();

        int version = client.checkExists().forPath(path).getVersion();
        assertNotEquals(b.fence(), version, "the node version is not the fence");
        SlotLease versionAsFence = new SlotLease(namespace, 0, b.owner(), version, b.expiresAt(), b.floor(), b.ceiling(), b.storeNow());
        assertTrue(slots.renew(versionAsFence, TTL, TOLERANCE).isEmpty(), "a lease is matched by the fence in the data");
        assertFalse(slots.release(versionAsFence, b.ceiling()));
        assertTrue(slots.renew(b, TTL, TOLERANCE).isPresent());
    }

    @Test
    void leaseTimesAreTheMtimeOfTheWrite() throws Exception {
        String namespace = newNamespace();
        SlotLeaseStore slots = store.slots();
        SlotLease lease = acquire(slots, namespace, "host-a", TTL);
        String path = ZookeeperSlotLeaseStore.slotPath(namespace, 0);
        Stat stat = client.checkExists().forPath(path);
        assertEquals(stat.getMtime(), lease.storeNow());
        assertEquals(stat.getMtime() + TTL.toMillis(), lease.expiresAt());

        SlotLease renewed = slots.renew(lease, TTL.multipliedBy(2), TOLERANCE).orElseThrow();
        stat = client.checkExists().forPath(path);
        assertEquals(stat.getMtime(), renewed.storeNow());
        assertEquals(stat.getMtime() + TTL.toMillis() * 2, renewed.expiresAt());
        String data = new String(client.getData().forPath(path), StandardCharsets.UTF_8);
        assertFalse(data.contains(Long.toString(renewed.expiresAt())), "the expiry follows from mtime + ttl, not stored: " + data);

        Stat clock = client.checkExists().forPath(ZookeeperSlotLeaseStore.namespacePath(namespace) + "/" + ZookeeperSlotLeaseStore.CLOCK);
        assertTrue(clock.getMtime() <= lease.storeNow(), "acquire wrote the clock node before it took the slot");
    }

    @Test
    void expiryIsCheckedAgainstTheClockNode() throws Exception {
        String namespace = newNamespace();
        SlotLeaseStore slots = store.slots();
        SlotLease a = acquire(slots, namespace, "host-a", Duration.ofMillis(300));
        Owner b = new Owner("host-b", UUID.randomUUID());
        AcquireResult result = slots.acquire(namespace, b, 1, TTL, TOLERANCE, OptionalInt.empty());
        assertEquals(a.expiresAt(), assertInstanceOf(AcquireResult.Exhausted.class, result).earliestExpiresAt());
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (result instanceof AcquireResult.Exhausted && System.nanoTime() < deadline) {
            Thread.sleep(50);
            result = slots.acquire(namespace, b, 1, TTL, TOLERANCE, OptionalInt.empty());
        }
        SlotLease taken = assertInstanceOf(AcquireResult.Acquired.class, result).lease();
        Stat clock = client.checkExists().forPath(ZookeeperSlotLeaseStore.namespacePath(namespace) + "/" + ZookeeperSlotLeaseStore.CLOCK);
        assertTrue(clock.getMtime() > a.expiresAt(), "the takeover saw a clock past the expiry: " + clock.getMtime() + " vs " + a.expiresAt());
        assertTrue(taken.floor() >= a.ceiling());
    }
}
