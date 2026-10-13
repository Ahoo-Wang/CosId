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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.lease.Lease;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * The replica wait with a real replica: writes acknowledged by it go through.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisReplicaWaitReplicaTest {
    private static final DockerImageName REDIS = DockerImageName.parse("redis:7.4-alpine");
    private Network network;
    private GenericContainer<?> primary;
    private GenericContainer<?> replica;
    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    
    @SuppressWarnings("resource")
    @BeforeAll
    void setup() throws InterruptedException {
        Assumptions.assumeTrue(RealRedis.clusterNodes() == null && DockerClientFactory.instance().isDockerAvailable(), "needs Docker");
        network = Network.newNetwork();
        primary = new GenericContainer<>(REDIS).withNetwork(network).withNetworkAliases("primary").withExposedPorts(6379);
        primary.start();
        replica = new GenericContainer<>(REDIS).withNetwork(network).withCommand("redis-server", "--replicaof", "primary", "6379");
        replica.start();
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(primary.getHost(), primary.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = RealRedis.createRedisTemplate(connectionFactory);
        for (int i = 0; i < 100 && !replicaConnected(); i++) {
            Thread.sleep(100);
        }
        Assumptions.assumeTrue(replicaConnected(), "the replica did not connect");
    }
    
    private boolean replicaConnected() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String replicaState = connection.serverCommands().info("replication").getProperty("slave0");
            return replicaState != null && replicaState.contains("state=online");
        }
    }
    
    @AfterAll
    void destroy() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (replica != null) {
            replica.stop();
        }
        if (primary != null) {
            primary.stop();
        }
        if (network != null) {
            network.close();
        }
    }
    
    @Test
    void claimsAndRenewalsAcknowledgedByTheReplica() {
        SpringRedisMachineLeaseStore store = new SpringRedisMachineLeaseStore(redisTemplate, RedisCommandTimeout.of(Duration.ofSeconds(5)),
            RedisReplicaWait.of(1, Duration.ofSeconds(2)));
        String namespace = "replica-" + UUID.randomUUID();
        
        Optional<Lease> claimed = store.claimNew(namespace, InstanceId.of("instance", false), 7, store.serverTime(namespace));
        
        assertThat(claimed.isPresent(), is(true));
        assertThat(store.renew(claimed.get(), store.serverTime(namespace)).isPresent(), is(true));
        assertThat(store.release(claimed.get(), store.serverTime(namespace)), is(false));
    }
    
    @Test
    void segmentsAcknowledgedByTheReplica() {
        IdSegmentDistributor distributor = new SpringRedisIdSegmentDistributorFactory(redisTemplate, RedisCommandTimeout.of(Duration.ofSeconds(5)),
            RedisReplicaWait.of(1, Duration.ofSeconds(2))).create(new IdSegmentDistributorDefinition("replica-" + UUID.randomUUID(), "order", 0, 10));
        
        assertThat(distributor.nextMaxId(), equalTo(10L));
        assertThat(distributor.nextMaxId(), equalTo(20L));
    }
}
