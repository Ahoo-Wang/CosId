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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.store.StoreTimeoutException;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.StoreProvider;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.test.InstanceSpec;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.ServiceLoader;
import java.util.UUID;

/**
 * The {@code zookeeper} store provider: found by {@link ServiceLoader}, on an injected client or one of its own.
 */
class ZookeeperStoreProviderTest {
    private static StoreConfig config(Map<String, Object> options, Optional<Object> client) {
        StoreConfig.Builder config = StoreConfig.builder("zk", ZookeeperStoreProvider.TYPE).timeout(Duration.ofSeconds(5)).options(options);
        client.ifPresent(config::client);
        return config.build();
    }

    @Test
    void serviceLoaderFindsTheProvider() {
        assertTrue(ServiceLoader.load(StoreProvider.class).stream()
            .anyMatch(provider -> provider.get().type().equals("zookeeper") && provider.type() == ZookeeperStoreProvider.class));
    }

    @Test
    void injectedClientIsUsedAndNotClosed() {
        try (CuratorFramework client = ZookeeperTests.newClient()) {
            try (Store store = new ZookeeperStoreProvider().open(config(Map.of(), Optional.of(client)))) {
                assertEquals(0, store.ranges().reserve("provider/" + UUID.randomUUID(), 1, 0).start());
            }
            assertEquals(CuratorFrameworkState.STARTED, client.getState(), "the caller owns the injected client");
        }
    }

    @Test
    void namespacedClientKeepsEveryNodeUnderItsNamespace() throws Exception {
        try (CuratorFramework client = ZookeeperTests.newClient()) {
            CuratorFramework chrooted = client.usingNamespace("tenant-" + UUID.randomUUID().toString().replace("-", ""));
            ZookeeperStore store = new ZookeeperStore(chrooted, ZookeeperTests.TIMEOUT);
            String namespace = "provider-" + UUID.randomUUID();
            Owner owner = new Owner("host-a", UUID.randomUUID());
            AcquireResult first = store.slots().acquire(namespace, owner, 4, Duration.ofSeconds(30), Duration.ofSeconds(1), OptionalInt.empty());
            AcquireResult again = store.slots().acquire(namespace, owner, 4, Duration.ofSeconds(30), Duration.ofSeconds(1), OptionalInt.empty());
            assertEquals(me.ahoo.cosid.lease.AcquireReason.IDEMPOTENT, assertInstanceOf(AcquireResult.Acquired.class, again).reason(),
                "the batched reads see the slot written under the client namespace");
            assertEquals(assertInstanceOf(AcquireResult.Acquired.class, first).lease().slot(), ((AcquireResult.Acquired) again).lease().slot());
            assertTrue(chrooted.checkExists().forPath(ZookeeperSlotLeaseStore.slotPath(namespace, 0)) != null);
            assertTrue(client.checkExists().forPath(ZookeeperSlotLeaseStore.slotPath(namespace, 0)) == null);
        }
    }

    @Test
    void ownClientFromTheOptionsIsClosedWithTheStore() {
        Map<String, Object> options = Map.of("connectString", ZookeeperTests.connectString(), "sessionTimeout", "30s",
            "connectionTimeout", Duration.ofSeconds(5), "blockUntilConnectedWait", 10_000, "retry.baseSleepTime", "PT0.05S",
            "retry.maxRetries", "3", "retry.maxSleep", "200ms");
        Store store = new ZookeeperStoreProvider().open(config(options, Optional.empty()));
        try {
            AcquireResult result = store.slots().acquire("provider-" + UUID.randomUUID(), new Owner("host-a", UUID.randomUUID()), 4,
                Duration.ofSeconds(30), Duration.ofSeconds(1), OptionalInt.empty());
            assertInstanceOf(AcquireResult.Acquired.class, result);
        } finally {
            store.close();
        }
    }

    @Test
    void rejectsAClientOfAnotherType() {
        assertThrows(IllegalArgumentException.class, () -> new ZookeeperStoreProvider().open(config(Map.of(), Optional.of("not a client"))));
    }

    @Test
    void failsWhenNoServerAnswers() {
        Map<String, Object> options = Map.of("connectString", "127.0.0.1:" + InstanceSpec.getRandomPort(), "blockUntilConnectedWait", "200ms");
        assertThrows(CosIdException.class, () -> new ZookeeperStoreProvider().open(config(options, Optional.empty())));
    }

    @Test
    void parsesDurations() {
        Duration none = Duration.ofSeconds(7);
        assertEquals(none, ZookeeperStoreProvider.duration(Map.of(), "x", none));
        assertEquals(Duration.ofMillis(250), ZookeeperStoreProvider.duration(Map.of("x", "250ms"), "x", none));
        assertEquals(Duration.ofSeconds(3), ZookeeperStoreProvider.duration(Map.of("x", "3s"), "x", none));
        assertEquals(Duration.ofMinutes(2), ZookeeperStoreProvider.duration(Map.of("x", "2m"), "x", none));
        assertEquals(Duration.ofHours(1), ZookeeperStoreProvider.duration(Map.of("x", "1h"), "x", none));
        assertEquals(Duration.ofSeconds(5), ZookeeperStoreProvider.duration(Map.of("x", "PT5S"), "x", none));
        assertEquals(Duration.ofMillis(42), ZookeeperStoreProvider.duration(Map.of("x", "42"), "x", none));
        assertEquals(Duration.ofMillis(42), ZookeeperStoreProvider.duration(Map.of("x", 42L), "x", none));
        assertThrows(IllegalArgumentException.class, () -> ZookeeperStoreProvider.duration(Map.of("x", "soon"), "x", none));
    }

    @Test
    void operationsTimeOutWhenTheServerIsGone() throws Exception {
        TestingServer server = new TestingServer(true);
        try (CuratorFramework client = ZookeeperTests.newClient(server.getConnectString())) {
            ZookeeperStore store = new ZookeeperStore(client, Duration.ofMillis(500));
            String key = "timeout/" + UUID.randomUUID();
            store.ranges().reserve(key, 1, 0);
            server.stop();
            long start = System.nanoTime();
            CosIdException failure = assertThrows(CosIdException.class, () -> store.ranges().reserve(key, 1, 0));
            assertTrue(StoreTimeoutException.isTimeout(failure), "a store timeout, reported by the runtime: " + failure);
            long millis = (System.nanoTime() - start) / 1_000_000;
            assertTrue(millis < 5_000, "the store timeout bounds the call: " + millis + " ms");
        } finally {
            server.close();
        }
    }

    @Test
    void rejectsInvalidNames() {
        try (CuratorFramework client = ZookeeperTests.newClient()) {
            ZookeeperStore store = new ZookeeperStore(client, ZookeeperTests.TIMEOUT);
            assertThrows(IllegalArgumentException.class, () -> store.ranges().reserve("a//b", 1, 0));
            assertThrows(IllegalArgumentException.class, () -> store.slots().acquire("a/b", new Owner("host-a", UUID.randomUUID()), 1,
                Duration.ofSeconds(30), Duration.ofSeconds(1), OptionalInt.empty()));
            assertThrows(IllegalArgumentException.class, () -> new ZookeeperStore(client, Duration.ZERO));
        }
    }
}
