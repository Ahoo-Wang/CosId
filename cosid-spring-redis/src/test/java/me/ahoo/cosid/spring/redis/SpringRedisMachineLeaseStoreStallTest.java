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
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InMemoryMachineStateStorage;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.machine.lease.MachineLeaseManager;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * A Redis stall: the distribution at startup, on the long distribute timeout, survives it; a guard, under the lock on the
 * short timeout, gives up after about that timeout.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpringRedisMachineLeaseStoreStallTest {
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(1);
    private LettuceConnectionFactory storeConnections;
    private LettuceConnectionFactory adminConnections;
    private SpringRedisMachineLeaseStore store;
    private StringRedisTemplate admin;
    
    @BeforeAll
    void setup() {
        storeConnections = RealRedis.createConnectionFactory();
        adminConnections = RealRedis.createConnectionFactory();
        store = new SpringRedisMachineLeaseStore(RealRedis.createRedisTemplate(storeConnections), RedisCommandTimeout.of(SHORT_TIMEOUT));
        admin = RealRedis.createRedisTemplate(adminConnections);
    }
    
    @AfterAll
    void destroy() {
        if (storeConnections != null) {
            storeConnections.destroy();
        }
        if (adminConnections != null) {
            adminConnections.destroy();
        }
    }
    
    private LeaseStoreMachineIdDistributor distributor() {
        SpringRedisMachineLeaseStore distributeStore = store.withCommandTimeout(RedisCommandTimeout.of(Duration.ofSeconds(10)));
        return new LeaseStoreMachineIdDistributor(new MachineLeaseManager(distributeStore), new MachineLeaseManager(store),
            new InMemoryMachineStateStorage(), ClockBackwardsSynchronizer.DEFAULT);
    }
    
    private void stallRedis(long millis) {
        // DEBUG SLEEP is disabled on Redis 7: CLIENT PAUSE holds every client's commands instead.
        admin.execute((RedisCallback<Object>) connection -> connection.execute("CLIENT",
            "PAUSE".getBytes(StandardCharsets.UTF_8), String.valueOf(millis).getBytes(StandardCharsets.UTF_8)));
    }
    
    @Test
    void startupDistributeSurvivesAStallLongerThanTheShortTimeout() {
        String namespace = "stall_" + MockIdGenerator.INSTANCE.generateAsString();
        stallRedis(1500);
        long start = System.nanoTime();
        
        MachineState state = distributor().distribute(namespace, 4, InstanceId.of("owner", false), Duration.ofMinutes(1));
        
        assertThat(state.getMachineId(), equalTo(0));
        assertThat(Duration.ofNanos(System.nanoTime() - start).toMillis(), greaterThanOrEqualTo(1300L));
    }
    
    @Test
    void guardGivesUpAfterTheShortTimeout() throws InterruptedException {
        String namespace = "stall_" + MockIdGenerator.INSTANCE.generateAsString();
        LeaseStoreMachineIdDistributor distributor = distributor();
        InstanceId owner = InstanceId.of("owner", false);
        distributor.distribute(namespace, 4, owner, Duration.ofMinutes(1));
        stallRedis(3000);
        long start = System.nanoTime();
        
        assertThrows(RuntimeException.class, () -> distributor.guard(namespace, owner, Duration.ofMinutes(1)));
        
        assertThat(Duration.ofNanos(System.nanoTime() - start).toMillis(), lessThan(2500L));
        Thread.sleep(3000);
    }
}
