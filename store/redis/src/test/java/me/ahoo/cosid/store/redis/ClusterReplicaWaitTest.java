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

import me.ahoo.cosid.lease.AcquireReason;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;

import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * {@code WAIT} on a Redis Cluster with replicas (rc-review B-B1, B-N4): the script and its {@code WAIT} run on one
 * connection to the node of the keys, so a write the frozen replicas did not acknowledge fails. Were {@code WAIT} on
 * another connection, one without writes, it would count the replicas at once and the write would pass.
 */
class ClusterReplicaWaitTest {
    /** Long enough for replicas in sync to acknowledge on a loaded runner; the frozen ones never do. */
    private static final Duration SHORT = Duration.ofSeconds(2);
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final Duration TOLERANCE = Duration.ofSeconds(1);
    private static RedisServers.Cluster cluster;

    @BeforeAll
    static void start() {
        cluster = RedisServers.clusterWithReplicas();
    }

    @AfterAll
    static void stop() {
        cluster.stop();
    }

    private static Store open(Duration timeout) {
        return new RedisStoreProvider().open(new StoreConfig("cluster-wait", RedisStoreProvider.TYPE, timeout, Map.of(), Optional.of(cluster.client())));
    }

    @Test
    void autoWaitsForOneReplicaOfEachPrimary() {
        try (StatefulRedisClusterConnection<String, String> connection = cluster.client().connect()) {
            assertEquals(1, LettuceRedisScripts.connectedReplicas(connection));
        }
        assertEquals(3, cluster.replicaPorts().size());
    }

    @Test
    void writesFailWhileTheReplicasAreFrozen() {
        List<String> keys = List.of("frozen-" + UUID.randomUUID(), "frozen-" + UUID.randomUUID(), "frozen-" + UUID.randomUUID());
        try (Store store = open(RedisServers.TIMEOUT)) {
            for (String key : keys) {
                assertEquals(0, store.ranges().reserve(key, 10, 0).start(), "the replicas acknowledge");
            }
        }
        List<String> pids = cluster.replicaPids();
        try (Store store = open(SHORT)) {
            cluster.signal("STOP", pids);
            for (String key : keys) {
                RedisStoreException failure = assertThrows(RedisStoreException.class, () -> store.ranges().reserve(key, 10, 0));
                assertThat(failure.getMessage(), containsString("acknowledged by 0 of 1 replicas"));
            }
        } finally {
            cluster.signal("CONT", pids);
        }
        cluster.awaitReplicasInSync();
    }

    @Test
    void idempotentAcquireWaitsForItsReplicaToo() {
        String namespace = "idempotent-" + UUID.randomUUID();
        Owner owner = new Owner("host", UUID.randomUUID());
        List<String> pids = cluster.replicaPids();
        try (Store store = open(SHORT)) {
            assertInstanceOf(AcquireResult.Acquired.class, store.slots().acquire(namespace, owner, 4, TTL, TOLERANCE, OptionalInt.empty()));
            cluster.signal("STOP", pids);
            // The first acquire's WAIT passed on this connection; only a write of the retry makes its WAIT wait again.
            RedisStoreException failure = assertThrows(RedisStoreException.class,
                () -> store.slots().acquire(namespace, owner, 4, TTL, TOLERANCE, OptionalInt.empty()));
            assertThat(failure.getMessage(), containsString("acknowledged by 0 of 1 replicas"));
        } finally {
            cluster.signal("CONT", pids);
        }
        cluster.awaitReplicasInSync();
        try (Store store = open(RedisServers.TIMEOUT)) {
            AcquireResult.Acquired retry = assertInstanceOf(AcquireResult.Acquired.class,
                store.slots().acquire(namespace, owner, 4, TTL, TOLERANCE, OptionalInt.empty()));
            assertEquals(AcquireReason.IDEMPOTENT, retry.reason());
        }
    }
}
