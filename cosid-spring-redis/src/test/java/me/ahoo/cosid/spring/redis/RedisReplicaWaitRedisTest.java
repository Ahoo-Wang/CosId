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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

/**
 * The replica wait on a Redis without replicas (standalone, or a cluster of primaries): {@code WAIT} runs on the node
 * that took the write, and too few replicas fail the write.
 */
class RedisReplicaWaitRedisTest {
    private static final RedisReplicaWait ONE_REPLICA = RedisReplicaWait.of(1, Duration.ofMillis(100));
    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    
    @BeforeEach
    void setup() {
        connectionFactory = RealRedis.createConnectionFactory();
        redisTemplate = RealRedis.createRedisTemplate(connectionFactory);
    }
    
    @AfterEach
    void destroy() {
        connectionFactory.destroy();
    }
    
    /**
     * The WAIT calls of the node that holds the key.
     */
    private long waitCalls(String key) {
        Properties stats;
        if (RealRedis.clusterNodes() != null) {
            try (RedisClusterConnection connection = connectionFactory.getClusterConnection()) {
                stats = connection.serverCommands().info(connection.clusterGetNodeForKey(key.getBytes(StandardCharsets.UTF_8)), "commandstats");
            }
        } else {
            try (RedisConnection connection = connectionFactory.getConnection()) {
                stats = connection.serverCommands().info("commandstats");
            }
        }
        String wait = stats.getProperty("cmdstat_wait");
        // calls=12,usec=34,...
        return wait == null ? 0 : Long.parseLong(wait.substring("calls=".length(), wait.indexOf(',')));
    }
    
    @Test
    void noWaitByDefault() {
        assertThat(RedisReplicaWait.of(0, null), is(RedisReplicaWait.NONE));
        assertThat(RedisReplicaWait.NONE.isEnabled(), is(false));
        assertThrows(IllegalArgumentException.class, () -> RedisReplicaWait.of(1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> RedisReplicaWait.of(-1, Duration.ofSeconds(1)));
        
        String namespace = "no-wait-" + UUID.randomUUID();
        SpringRedisMachineLeaseStore store = new SpringRedisMachineLeaseStore(redisTemplate, RedisCommandTimeout.of(Duration.ofSeconds(5)));
        String key = SpringRedisMachineLeaseStore.keys(namespace).get(0);
        long before = waitCalls(key);
        
        assertThat(store.claimNew(namespace, InstanceId.of("instance", false), 7, store.serverTime(namespace)).isPresent(), is(true));
        
        assertThat(waitCalls(key), equalTo(before));
    }
    
    @Test
    void aClaimWithTooFewReplicasFails() {
        String namespace = "wait-claim-" + UUID.randomUUID();
        SpringRedisMachineLeaseStore store = new SpringRedisMachineLeaseStore(redisTemplate, RedisCommandTimeout.of(Duration.ofSeconds(5)), ONE_REPLICA);
        String key = SpringRedisMachineLeaseStore.keys(namespace).get(0);
        long before = waitCalls(key);
        
        RedisReplicaWaitException exception = assertThrows(RedisReplicaWaitException.class,
            () -> store.claimNew(namespace, InstanceId.of("instance", false), 7, store.serverTime(namespace)));
        
        assertThat(exception.getOperation(), equalTo("claimNew"));
        assertThat(exception.getAcknowledged(), equalTo(0L));
        assertThat(exception.getMessage(), containsString("0 of 1 replicas"));
        assertThat(waitCalls(key), equalTo(before + 1));
    }
    
    @Test
    void aSegmentWithTooFewReplicasFails() {
        SpringRedisIdSegmentDistributorFactory factory = new SpringRedisIdSegmentDistributorFactory(redisTemplate, RedisCommandTimeout.of(Duration.ofSeconds(5)),
            ONE_REPLICA);
        SpringRedisIdSegmentDistributor distributor = (SpringRedisIdSegmentDistributor) factory.create(
            new IdSegmentDistributorDefinition("wait-segment-" + UUID.randomUUID(), "order", 0, 10));
        long before = waitCalls(distributor.getAdderKey());
        
        RedisReplicaWaitException exception = assertThrows(RedisReplicaWaitException.class, distributor::nextMaxId);
        
        assertThat(exception.getOperation(), equalTo("nextMaxId"));
        assertThat(waitCalls(distributor.getAdderKey()), equalTo(before + 1));
    }
}
