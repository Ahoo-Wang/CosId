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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.StoreProvider;

import io.lettuce.core.cluster.models.partitions.RedisClusterNode;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.ServiceLoader;
import java.util.UUID;

class RedisStoreProviderTest {
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final Duration TOLERANCE = Duration.ofSeconds(1);
    private static final Owner OWNER = new Owner("host", UUID.randomUUID());

    private static StoreConfig config(Map<String, Object> options, Object client) {
        StoreConfig.Builder config = StoreConfig.builder("test", RedisStoreProvider.TYPE).timeout(RedisServers.TIMEOUT).options(options);
        Optional.ofNullable(client).ifPresent(config::client);
        return config.build();
    }

    @Test
    void isFoundByServiceLoader() {
        List<String> types = ServiceLoader.load(StoreProvider.class).stream().map(provider -> provider.get().type()).toList();
        assertTrue(types.contains("redis"), types::toString);
    }

    @Test
    void opensItsOwnClientFromTheUri() {
        try (Store store = new RedisStoreProvider().open(config(Map.of("uri", RedisServers.standaloneUri(), "sweepBatch", "4", "wait", "auto"), null))) {
            assertEquals(0, store.ranges().reserve("uri-" + UUID.randomUUID(), 5, 0).start());
        }
    }

    @Test
    void shutsItsOwnClusterClientDownWhenItCannotConnect() {
        RedisClusterNode node = RedisServers.cluster().getPartitions().iterator().next();
        String uri = "redis://" + node.getUri().getHost() + ":" + node.getUri().getPort();
        // The node addresses resolve only through the mapping of the test client, so a client of the store's own fails to connect.
        assertThrows(RuntimeException.class, () -> new RedisStoreProvider().open(config(Map.of("uri", uri, "cluster", true), null)));
    }

    @Test
    void rejectsWhatItCannotOpen() {
        RedisStoreProvider provider = new RedisStoreProvider();
        assertThrows(IllegalArgumentException.class, () -> provider.open(config(Map.of(), null)));
        assertThrows(IllegalArgumentException.class, () -> provider.open(config(Map.of(), "redis://localhost")));
        assertThrows(IllegalArgumentException.class, () -> provider.open(config(Map.of("wait", "never"), RedisServers.standalone())));
        assertThrows(IllegalArgumentException.class, () -> provider.open(config(Map.of("sweepBatch", 0), RedisServers.standalone())));
    }

    @Test
    void usesInjectedScriptsAsTheyAre() {
        List<List<String>> calls = new ArrayList<>();
        RedisScripts scripts = (sha, script, keys, args) -> {
            assertEquals(Script.sha1(script), sha);
            calls.add(keys);
            return List.of("reserved", "15");
        };
        try (Store store = new RedisStoreProvider().open(config(Map.of(), scripts))) {
            assertEquals(5, store.ranges().reserve("k", 10, 0).start());
            assertEquals(List.of(List.of("cosid:{k}", "cosid:{k}:sealed")), calls);
        }
    }

    @Test
    void validatesArgumentsAndReplies() {
        RedisScripts unexpected = (sha, script, keys, args) -> List.of("what");
        try (RedisStore store = new RedisStore(unexpected)) {
            assertThrows(IllegalArgumentException.class, () -> store.reserve("k", 0, 0));
            assertThrows(IllegalArgumentException.class, () -> store.reserve("k", 1, -1));
            assertThrows(IllegalArgumentException.class, () -> store.acquire("ns", OWNER, 0, TTL, TOLERANCE, OptionalInt.empty()));
            assertThrows(IllegalArgumentException.class, () -> store.acquire("ns", OWNER, 1, Duration.ZERO, TOLERANCE, OptionalInt.empty()));
            assertThrows(IllegalArgumentException.class, () -> store.acquire("ns", OWNER, 1, TTL, Duration.ofSeconds(-1), OptionalInt.empty()));
            SlotLease lease = SlotLease.builder()
                .namespace("ns")
                .slot(0)
                .owner(OWNER)
                .fence(1)
                .expiresAt(0)
                .floor(0)
                .ceiling(0)
                .storeNow(0)
                .build();
            assertThrows(RedisStoreException.class, () -> store.reserve("k", 1, 0));
            assertThrows(RedisStoreException.class, () -> store.acquire("ns", OWNER, 1, TTL, TOLERANCE, OptionalInt.empty()));
            assertThrows(RedisStoreException.class, () -> store.renew(lease, TTL, TOLERANCE));
            assertThrows(RedisStoreException.class, () -> store.release(lease, 0));
        }
        try (RedisStore empty = new RedisStore((sha, script, keys, args) -> List.of())) {
            assertThrows(RedisStoreException.class, () -> empty.seal("k"));
        }
        assertThrows(IllegalArgumentException.class, () -> new RedisStore((sha, script, keys, args) -> List.of(), 0, () -> {
        }));
        RedisStore failing = new RedisStore((sha, script, keys, args) -> List.of(), 1, () -> {
            throw new IllegalStateException("close");
        });
        assertInstanceOf(IllegalStateException.class, assertThrows(RedisStoreException.class, failing::close).getCause());
    }

    @Test
    void storeTimeoutBoundsACommandToAServerThatStoppedAnswering() {
        // A container of its own: pausing it freezes the server with its socket open, so only the store timeout ends the call.
        try (GenericContainer<?> redis = new GenericContainer<>(RedisServers.IMAGE).withExposedPorts(6379)) {
            redis.start();
            String uri = "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379);
            StoreConfig config = StoreConfig.builder("paused", RedisStoreProvider.TYPE)
                .timeout(Duration.ofMillis(500))
                .option("uri", uri)
                .option("wait", "off")
                .build();
            try (Store store = new RedisStoreProvider().open(config)) {
                String key = "timeout-" + UUID.randomUUID();
                assertEquals(0, store.ranges().reserve(key, 1, 0).start());
                var docker = DockerClientFactory.instance().client();
                docker.pauseContainerCmd(redis.getContainerId()).exec();
                try {
                    long start = System.nanoTime();
                    RedisStoreException error = assertThrows(RedisStoreException.class, () -> store.ranges().reserve(key, 1, 0));
                    long millis = (System.nanoTime() - start) / 1_000_000;
                    assertTrue(error.getMessage().contains("did not answer within PT0.5S"), error::getMessage);
                    assertTrue(millis >= 450 && millis < 5_000, "the store timeout bounds the call: " + millis + " ms");
                } finally {
                    docker.unpauseContainerCmd(redis.getContainerId()).exec();
                }
            }
        }
    }

    @Test
    void acquireReportsExhaustedWithTheEarliestExpiry() {
        RedisScripts exhausted = (sha, script, keys, args) -> List.of("exhausted", "1234");
        try (RedisStore store = new RedisStore(exhausted)) {
            assertEquals(new AcquireResult.Exhausted(1234), store.acquire("ns", OWNER, 1, TTL, TOLERANCE, OptionalInt.of(0)));
        }
    }
}
