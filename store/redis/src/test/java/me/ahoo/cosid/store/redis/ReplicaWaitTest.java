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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * {@code WAIT} (06-stores.md §2.2): {@code auto} waits for one replica when there are replicas, runs on a connection of
 * the store's own, and fails the operation when the replicas do not acknowledge.
 */
class ReplicaWaitTest {
    private static final Duration SHORT = Duration.ofMillis(500);

    private static Store open(Object client, Duration timeout, Map<String, Object> options) {
        return new RedisStoreProvider().open(StoreConfig.builder("wait", RedisStoreProvider.TYPE)
            .timeout(timeout)
            .options(options)
            .client(client)
            .build());
    }

    @Test
    void autoWaitsForOneReplicaOnlyWhenThereAreReplicas() {
        try (StatefulRedisConnection<String, String> standalone = RedisServers.standalone().connect();
             StatefulRedisConnection<String, String> primary = RedisServers.sharedReplicated().primary().connect()) {
            assertEquals(0, RedisStoreProvider.replicas("auto", () -> LettuceRedisScripts.connectedReplicas(standalone, RedisServers.TIMEOUT)));
            assertEquals(1, RedisStoreProvider.replicas("auto", () -> LettuceRedisScripts.connectedReplicas(primary, RedisServers.TIMEOUT)));
        }
        assertEquals(0, RedisStoreProvider.replicas("off", () -> 3));
        assertEquals(2, RedisStoreProvider.replicas("2", () -> 3));
        assertThrows(IllegalArgumentException.class, () -> RedisStoreProvider.replicas("some", () -> 1));
        assertThrows(IllegalArgumentException.class, () -> RedisStoreProvider.replicas("-1", () -> 1));
    }

    @Test
    void failsWhenFewerReplicasAcknowledge() {
        // WAIT 2 never gets its second acknowledgement, so every call takes the whole timeout: long enough for the one
        // replica in sync to acknowledge, short enough for the test.
        try (Store store = open(RedisServers.sharedReplicated().primary(), Duration.ofSeconds(2), Map.of("wait", "2"))) {
            RedisStoreException failure = assertThrows(RedisStoreException.class, () -> store.ranges().reserve("wait-" + UUID.randomUUID(), 10, 0));
            assertThat(failure.getMessage(), containsString("acknowledged by 1 of 2 replicas"));
            assertThrows(RedisStoreException.class, () -> store.slots().acquire("wait-" + UUID.randomUUID(), new Owner("host", UUID.randomUUID()), 4,
                Duration.ofSeconds(10), Duration.ofSeconds(1), 0, OptionalInt.empty()));
        }
    }

    @Test
    void failsOnceTheReplicaIsGone() {
        RedisServers.Replicated pair = RedisServers.replicated();
        try {
            String key = "gone-" + UUID.randomUUID();
            // The setup write may take as long as a loaded machine needs; only the write after the loss is held to SHORT.
            try (Store store = open(pair.primary(), RedisServers.TIMEOUT, Map.of())) {
                assertEquals(0, store.ranges().reserve(key, 10, 0).start(), "auto found the replica and it acknowledges");
            }
            try (Store store = open(pair.primary(), SHORT, Map.of())) {
                try (StatefulRedisConnection<String, String> replica = pair.replica().connect()) {
                    replica.sync().replicaofNoOne();
                }
                RedisStoreException failure = assertThrows(RedisStoreException.class, () -> store.ranges().reserve(key, 10, 0));
                assertThat(failure.getMessage(), containsString("acknowledged by 0 of 1 replicas"));
            }
        } finally {
            pair.primary().shutdown();
            pair.replica().shutdown();
            pair.replicaContainer().stop();
            pair.primaryContainer().stop();
        }
    }

    @Test
    void neverWaitsOnAnInjectedConnection() {
        try (StatefulRedisConnection<String, String> shared = RedisServers.sharedReplicated().primary().connect()) {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> open(shared, SHORT, Map.of()));
            assertThat(failure.getMessage(), containsString("options.wait=off"));
            try (Store store = open(shared, SHORT, Map.of("wait", "off"))) {
                assertEquals(0, store.ranges().reserve("shared-" + UUID.randomUUID(), 10, 0).start());
            }
            assertEquals("PONG", shared.sync().ping(), "closing the store leaves the injected connection open");
        }
    }

    @Test
    void waitsOnTheClusterNodeOfTheKeys() {
        try (StatefulRedisClusterConnection<String, String> connection = RedisServers.cluster().connect()) {
            assertEquals(0, LettuceRedisScripts.fewestReplicas(connection, RedisServers.TIMEOUT), "the test cluster has no replicas");
        }
        try (Store store = open(RedisServers.cluster(), SHORT, Map.of("wait", "1"))) {
            RedisStoreException failure = assertThrows(RedisStoreException.class, () -> store.ranges().reserve("cluster-" + UUID.randomUUID(), 10, 0));
            assertThat(failure.getMessage(), containsString("acknowledged by 0 of 1 replicas"));
        }
        try (StatefulRedisClusterConnection<String, String> shared = RedisServers.cluster().connect();
             Store store = open(shared, SHORT, Map.of())) {
            assertInstanceOf(AcquireResult.Acquired.class, store.slots().acquire("cluster-" + UUID.randomUUID(), new Owner("host", UUID.randomUUID()), 4,
                Duration.ofSeconds(10), Duration.ofSeconds(1), 0, OptionalInt.empty()));
        }
    }
}
