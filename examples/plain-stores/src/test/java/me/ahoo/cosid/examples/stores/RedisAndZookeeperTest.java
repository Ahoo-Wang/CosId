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

package me.ahoo.cosid.examples.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.StoreProvider;

import io.netty.util.Version;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * L14: {@code store/redis} (Lettuce 7, Netty 4.2) and {@code store/zookeeper} on one plain-Java classpath. ZooKeeper
 * used to bring Netty 4.1, {@code netty-transport-native-epoll} among it, and Lettuce then failed on Linux with
 * {@code NoClassDefFoundError: io/netty/channel/epoll/EpollIoHandler}. The classpath check holds on every OS; the
 * reservations reproduce the failure on Linux (the CI runners).
 */
class RedisAndZookeeperTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Test
    void everyNettyArtifactOnTheClasspathHasOneVersion() {
        Map<String, Version> netty = Version.identify();
        Set<String> versions = netty.values().stream().map(Version::artifactVersion).collect(Collectors.toCollection(TreeSet::new));
        assertEquals(1, versions.size(), () -> "Netty artifacts of mixed versions: " + netty);
        assertFalse(netty.isEmpty(), "Lettuce brings Netty");
        assertTrue(versions.iterator().next().startsWith("4.2."), () -> "Lettuce 7 needs Netty 4.2: " + versions);
    }

    @Test
    void zookeeperClientUsesItsDefaultNioSocket() {
        assertNull(System.getProperty("zookeeper.clientCnxnSocket"), "nothing selects ClientCnxnSocketNetty");
    }

    @Test
    void bothStoresReserveOnOneClasspath() {
        try (GenericContainer<?> redis = new GenericContainer<>("redis:8.10-alpine").withExposedPorts(6379);
             GenericContainer<?> zookeeper = new GenericContainer<>("zookeeper:3.9").withExposedPorts(2181)) {
            redis.start();
            zookeeper.start();
            String redisUri = "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379);
            String connectString = zookeeper.getHost() + ":" + zookeeper.getMappedPort(2181);
            String key = "plain/" + UUID.randomUUID();
            try (Store redisStore = provider("redis").open(new StoreConfig("redis", "redis", TIMEOUT, Map.of("uri", redisUri), Optional.empty()));
                 Store zookeeperStore = provider("zookeeper").open(new StoreConfig("zookeeper", "zookeeper", TIMEOUT,
                     Map.of("connectString", connectString, "blockUntilConnectedWait", "30s"), Optional.empty()))) {
                assertEquals(new Range(0, 10), redisStore.ranges().reserve(key, 10, 0));
                assertEquals(new Range(0, 10), zookeeperStore.ranges().reserve(key, 10, 0));
                assertEquals(new Range(10, 20), redisStore.ranges().reserve(key, 10, 0));
                assertEquals(new Range(10, 20), zookeeperStore.ranges().reserve(key, 10, 0));
            }
        }
    }

    private static StoreProvider provider(String type) {
        return ServiceLoader.load(StoreProvider.class).stream().map(ServiceLoader.Provider::get)
            .filter(provider -> provider.type().equals(type)).findFirst()
            .orElseThrow(() -> new AssertionError("no " + type + " store provider on the classpath"));
    }
}
