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

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InMemoryMachineStateStorage;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineIdOverflowException;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

/**
 * Cases of {@link SpringRedisMachineIdDistributor} beyond the shared spec, on a real Redis.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpringRedisMachineIdDistributorRedisTest {
    private static final Duration SAFE_GUARD_DURATION = Duration.ofSeconds(10);
    
    LettuceConnectionFactory connectionFactory;
    StringRedisTemplate redisTemplate;
    MachineStateStorage storage;
    String namespace;
    
    @BeforeAll
    void setupRedis() {
        connectionFactory = RealRedis.createConnectionFactory();
        redisTemplate = RealRedis.createRedisTemplate(connectionFactory);
    }
    
    @AfterAll
    void destroy() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }
    
    @BeforeEach
    void setup() {
        storage = new InMemoryMachineStateStorage();
        namespace = MockIdGenerator.INSTANCE.generateAsString();
    }
    
    private SpringRedisMachineIdDistributor distributor() {
        return new SpringRedisMachineIdDistributor(redisTemplate, storage, ClockBackwardsSynchronizer.DEFAULT);
    }
    
    private String key(String name) {
        return "cosid:" + SpringRedisMachineIdDistributor.hashTag(namespace) + ":" + name;
    }
    
    private Object instanceState(InstanceId instanceId) {
        return redisTemplate.opsForHash().get(key("itc_idx"), instanceId.getInstanceId());
    }
    
    @Test
    void distributeShouldRecordTheInstance() {
        InstanceId instanceId = InstanceId.of("127.0.0.1", 8080, false);
        
        MachineState actual = distributor().distribute(namespace, 5, instanceId, SAFE_GUARD_DURATION);
        
        assertThat(actual.getMachineId(), equalTo(0));
        assertThat(storage.get(namespace, instanceId).getMachineId(), equalTo(0));
        assertThat(String.valueOf(instanceState(instanceId)).startsWith("0|"), equalTo(true));
    }
    
    /**
     * Taking back its own machine id never moves the last timestamp backwards, so the clock-backwards guard still waits.
     */
    @Test
    void distributeBySelfShouldNeverLowerLastStamp() {
        InstanceId instanceId = InstanceId.of("127.0.0.1", 8085, false);
        long future = System.currentTimeMillis() + Duration.ofMinutes(1).toMillis();
        redisTemplate.opsForValue().set(key("adder"), "3");
        redisTemplate.opsForHash().put(key("itc_idx"), instanceId.getInstanceId(), "3|" + future);
        
        MachineState actual = new SpringRedisMachineIdDistributor(redisTemplate, storage, ClockBackwardsSynchronizer.DEFAULT) {
            MachineState distributeRemoteForTest() {
                return distributeRemote(namespace, 5, instanceId, SAFE_GUARD_DURATION);
            }
        }.distributeRemoteForTest();
        
        assertThat(actual, equalTo(MachineState.of(3, future)));
        assertThat(instanceState(instanceId), equalTo("3|" + future));
    }
    
    @Test
    void distributeShouldThrowOverflowWhenEveryMachineIdIsTaken() {
        SpringRedisMachineIdDistributor distributor = distributor();
        distributor.distribute(namespace, 1, InstanceId.of("127.0.0.1", 1, false), SAFE_GUARD_DURATION);
        distributor.distribute(namespace, 1, InstanceId.of("127.0.0.1", 2, false), SAFE_GUARD_DURATION);
        
        Assertions.assertThrows(MachineIdOverflowException.class,
            () -> distributor.distribute(namespace, 1, InstanceId.of("127.0.0.1", 3, false), SAFE_GUARD_DURATION));
    }
    
    @Test
    void guardShouldThrowLostWhenTheInstanceNoLongerHoldsTheMachineId() {
        InstanceId instanceId = InstanceId.of("127.0.0.1", 8082, false);
        storage.set(namespace, 4, instanceId);
        
        Assertions.assertThrows(MachineIdLostException.class, () -> distributor().guard(namespace, instanceId, SAFE_GUARD_DURATION));
    }
    
    @Test
    void revertShouldReleaseAnUnstableInstance() {
        InstanceId instanceId = InstanceId.of("127.0.0.1", 8083, false);
        SpringRedisMachineIdDistributor distributor = distributor();
        int machineId = distributor.distribute(namespace, 5, instanceId, SAFE_GUARD_DURATION).getMachineId();
        
        distributor.revert(namespace, instanceId);
        
        assertThat(instanceState(instanceId), equalTo(null));
        assertThat(redisTemplate.opsForHash().hasKey(key("revert"), String.valueOf(machineId)), equalTo(true));
        assertThat(storage.get(namespace, instanceId), equalTo(MachineState.NOT_FOUND));
    }
    
    @Test
    void revertShouldKeepTheMachineIdOfAStableInstance() {
        InstanceId instanceId = InstanceId.of("127.0.0.1", 8084, true);
        SpringRedisMachineIdDistributor distributor = distributor();
        int machineId = distributor.distribute(namespace, 5, instanceId, SAFE_GUARD_DURATION).getMachineId();
        
        distributor.revert(namespace, instanceId);
        
        assertThat(String.valueOf(instanceState(instanceId)).startsWith(machineId + "|"), equalTo(true));
        assertThat(redisTemplate.opsForHash().hasKey(key("revert"), String.valueOf(machineId)), equalTo(false));
        assertThat(storage.get(namespace, instanceId), equalTo(MachineState.NOT_FOUND));
    }
    
    @Test
    void distributeShouldTimeOutWhenRedisHangs() {
        StringRedisTemplate hangingTemplate = new StringRedisTemplate() {
            @Override
            public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
                try {
                    Thread.sleep(Duration.ofSeconds(30).toMillis());
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }
        };
        SpringRedisMachineIdDistributor distributor = new SpringRedisMachineIdDistributor(hangingTemplate, storage,
            ClockBackwardsSynchronizer.DEFAULT, RedisCommandTimeout.of(Duration.ofSeconds(1)), RedisCommandTimeout.of(Duration.ofMillis(200)));
        
        Assertions.assertThrows(RedisCommandTimeoutException.class,
            () -> distributor.distribute(namespace, 5, InstanceId.of("127.0.0.1", 8086, false), SAFE_GUARD_DURATION));
    }
}
