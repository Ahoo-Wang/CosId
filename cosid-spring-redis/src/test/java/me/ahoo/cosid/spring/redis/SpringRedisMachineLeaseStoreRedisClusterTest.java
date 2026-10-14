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

package me.ahoo.cosid.spring.redis;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.lease.Lease;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * On a Redis Cluster, the namespaces of the lease store spread over the nodes, and each one is served by the node of
 * its slot, its clock included.
 */
@EnabledIfEnvironmentVariable(named = RealRedis.CLUSTER_NODES_ENV, matches = ".+")
class SpringRedisMachineLeaseStoreRedisClusterTest {
    private LettuceConnectionFactory connectionFactory;
    private SpringRedisMachineLeaseStore store;
    
    @BeforeEach
    void setup() {
        connectionFactory = RealRedis.createConnectionFactory();
        store = new SpringRedisMachineLeaseStore(RealRedis.createRedisTemplate(connectionFactory), RedisCommandTimeout.of(Duration.ofSeconds(5)));
    }
    
    @AfterEach
    void destroy() {
        connectionFactory.destroy();
    }
    
    @Test
    void namespacesOnEveryNodeClaimAndRenew() {
        Set<RedisClusterNode> nodes = new HashSet<>();
        try (RedisClusterConnection connection = connectionFactory.getClusterConnection()) {
            for (int i = 0; i < 32; i++) {
                String namespace = "cluster-" + UUID.randomUUID();
                nodes.add(connection.clusterGetNodeForKey(SpringRedisMachineLeaseStore.keys(namespace).get(0).getBytes(StandardCharsets.UTF_8)));
                InstanceId instanceId = InstanceId.of("instance", false);
                long now = store.serverTime(namespace);
                Optional<Lease> claimed = store.claimNew(namespace, instanceId, 7, now);
                assertThat(claimed.isPresent(), is(true));
                assertThat(store.renew(claimed.get(), store.serverTime(namespace)).isPresent(), is(true));
            }
        }
        assertThat(nodes.size(), greaterThan(1));
    }
    
    private static long timeCalls(RedisClusterConnection connection, RedisClusterNode node) {
        Properties stats = connection.serverCommands().info(node, "commandstats");
        String time = stats.getProperty("cmdstat_time");
        if (time == null) {
            return 0;
        }
        // calls=12,usec=34,...
        return Long.parseLong(time.substring("calls=".length(), time.indexOf(',')));
    }
    
    @Test
    void theServerTimeOfANamespaceRunsOnTheNodeOfItsSlot() {
        try (RedisClusterConnection connection = connectionFactory.getClusterConnection()) {
            for (int i = 0; i < 8; i++) {
                String namespace = "cluster-stats-" + UUID.randomUUID();
                RedisClusterNode owner = connection.clusterGetNodeForKey(SpringRedisMachineLeaseStore.keys(namespace).get(0).getBytes(StandardCharsets.UTF_8));
                Map<RedisClusterNode, Long> before = new HashMap<>();
                for (RedisClusterNode master : connection.clusterGetNodes()) {
                    if (master.isMaster()) {
                        before.put(master, timeCalls(connection, master));
                    }
                }
                
                store.serverTime(namespace);
                
                for (Map.Entry<RedisClusterNode, Long> entry : before.entrySet()) {
                    long calls = timeCalls(connection, entry.getKey()) - entry.getValue();
                    // INFO itself is no TIME call; only the owner of the slot answered.
                    assertThat(entry.getKey() + " for " + namespace, calls, is(entry.getKey().getId().equals(owner.getId()) ? 1L : 0L));
                }
            }
        }
    }
    
    @Test
    void theServerTimeOfANamespaceIsItsNodesClock() {
        String namespace = "cluster-time-" + UUID.randomUUID();
        try (RedisClusterConnection connection = connectionFactory.getClusterConnection()) {
            RedisClusterNode node = connection.clusterGetNodeForKey(SpringRedisMachineLeaseStore.keys(namespace).get(0).getBytes(StandardCharsets.UTF_8));
            long before = connection.serverCommands().time(node, TimeUnit.MILLISECONDS);
            long serverTime = store.serverTime(namespace);
            long after = connection.serverCommands().time(node, TimeUnit.MILLISECONDS);
            assertThat(serverTime, greaterThan(before - 1));
            assertThat(serverTime, lessThan(after + 1));
        }
    }
}
