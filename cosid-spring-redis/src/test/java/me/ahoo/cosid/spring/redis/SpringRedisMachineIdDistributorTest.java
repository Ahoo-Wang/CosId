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
import static org.hamcrest.Matchers.lessThanOrEqualTo;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineIdOverflowException;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.MachineStateStorage;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class SpringRedisMachineIdDistributorTest {
    private static final String NAMESPACE = "machine-ns";
    private static final Duration SAFE_GUARD_DURATION = Duration.ofSeconds(10);

    @Test
    void distributeShouldReturnScriptMachineStateAndPassRedisArguments() {
        FakeStringRedisTemplate redisTemplate = new FakeStringRedisTemplate();
        TestMachineStateStorage storage = new TestMachineStateStorage();
        SpringRedisMachineIdDistributor distributor = distributor(redisTemplate, storage);
        InstanceId instanceId = InstanceId.of("127.0.0.1", 8080, false);
        redisTemplate.enqueueScriptResult(List.of(3L, 123L));
        long before = System.currentTimeMillis();

        MachineState machineState = distributor.distribute(NAMESPACE, 5, instanceId, SAFE_GUARD_DURATION);
        long after = System.currentTimeMillis();

        assertThat(machineState.getMachineId(), equalTo(3));
        assertThat(machineState.getLastTimeStamp(), equalTo(123L));
        FakeStringRedisTemplate.ScriptCall<?> call = redisTemplate.getScriptCalls().get(0);
        assertThat(call.getScript(), equalTo(SpringRedisMachineIdDistributor.MACHINE_ID_DISTRIBUTE));
        assertThat(call.getKeys(), equalTo(List.of("{machine-ns}")));
        Object[] args = call.getArgs();
        assertThat(args[0], equalTo("127.0.0.1:8080"));
        assertThat(args[1], equalTo("31"));
        assertLongStringBetween(args[2], before, after);
        assertLongStringBetween(args[3], before - SAFE_GUARD_DURATION.toMillis(), after - SAFE_GUARD_DURATION.toMillis());
        assertThat(storage.get(NAMESPACE, instanceId).getMachineId(), equalTo(3));
    }

    @Test
    void distributeShouldThrowOverflowWhenScriptReturnsMinusOne() {
        FakeStringRedisTemplate redisTemplate = new FakeStringRedisTemplate();
        SpringRedisMachineIdDistributor distributor = distributor(redisTemplate, new TestMachineStateStorage());
        InstanceId instanceId = InstanceId.of("127.0.0.1", 8081, false);
        redisTemplate.enqueueScriptResult(List.of(-1L, -1L));

        Assertions.assertThrows(
            MachineIdOverflowException.class,
            () -> distributor.distribute(NAMESPACE, 5, instanceId, SAFE_GUARD_DURATION)
        );
    }

    @Test
    void guardShouldThrowLostWhenScriptReturnsZero() {
        FakeStringRedisTemplate redisTemplate = new FakeStringRedisTemplate();
        TestMachineStateStorage storage = new TestMachineStateStorage();
        SpringRedisMachineIdDistributor distributor = distributor(redisTemplate, storage);
        InstanceId instanceId = InstanceId.of("127.0.0.1", 8082, false);
        storage.put(NAMESPACE, instanceId, MachineState.of(4, System.currentTimeMillis()));
        redisTemplate.enqueueScriptResult(0L);

        Assertions.assertThrows(
            MachineIdLostException.class,
            () -> distributor.guard(NAMESPACE, instanceId, SAFE_GUARD_DURATION)
        );
        FakeStringRedisTemplate.ScriptCall<?> call = redisTemplate.getScriptCalls().get(0);
        assertThat(call.getScript(), equalTo(SpringRedisMachineIdDistributor.MACHINE_ID_GUARD));
        assertThat(call.getKeys(), equalTo(List.of("{machine-ns}")));
        assertThat(call.getArgs()[0], equalTo("127.0.0.1:8082"));
    }

    @Test
    void revertShouldUseDefaultScriptForUnstableInstance() {
        FakeStringRedisTemplate redisTemplate = new FakeStringRedisTemplate();
        TestMachineStateStorage storage = new TestMachineStateStorage();
        SpringRedisMachineIdDistributor distributor = distributor(redisTemplate, storage);
        InstanceId instanceId = InstanceId.of("127.0.0.1", 8083, false);
        long lastStamp = System.currentTimeMillis() + 1_000;
        storage.put(NAMESPACE, instanceId, MachineState.of(5, lastStamp));

        distributor.revert(NAMESPACE, instanceId);

        FakeStringRedisTemplate.ScriptCall<?> call = redisTemplate.getScriptCalls().get(0);
        assertThat(call.getScript(), equalTo(SpringRedisMachineIdDistributor.MACHINE_ID_REVERT));
        assertThat(call.getKeys(), equalTo(List.of("{machine-ns}")));
        Assertions.assertArrayEquals(new Object[]{"127.0.0.1:8083", String.valueOf(lastStamp)}, call.getArgs());
        assertThat(storage.get(NAMESPACE, instanceId), equalTo(MachineState.NOT_FOUND));
    }

    @Test
    void revertShouldUseStableScriptForStableInstance() {
        FakeStringRedisTemplate redisTemplate = new FakeStringRedisTemplate();
        TestMachineStateStorage storage = new TestMachineStateStorage();
        SpringRedisMachineIdDistributor distributor = distributor(redisTemplate, storage);
        InstanceId instanceId = InstanceId.of("127.0.0.1", 8084, true);
        long lastStamp = System.currentTimeMillis() + 1_000;
        storage.put(NAMESPACE, instanceId, MachineState.of(6, lastStamp));

        distributor.revert(NAMESPACE, instanceId);

        FakeStringRedisTemplate.ScriptCall<?> call = redisTemplate.getScriptCalls().get(0);
        assertThat(call.getScript(), equalTo(SpringRedisMachineIdDistributor.MACHINE_ID_REVERT_STABLE));
        assertThat(call.getKeys(), equalTo(List.of("{machine-ns}")));
        Assertions.assertArrayEquals(new Object[]{"127.0.0.1:8084", String.valueOf(lastStamp)}, call.getArgs());
        assertThat(storage.get(NAMESPACE, instanceId), equalTo(MachineState.NOT_FOUND));
    }

    private static SpringRedisMachineIdDistributor distributor(FakeStringRedisTemplate redisTemplate, MachineStateStorage storage) {
        return new SpringRedisMachineIdDistributor(redisTemplate, storage, ClockBackwardsSynchronizer.DEFAULT);
    }

    private static void assertLongStringBetween(Object actual, long fromInclusive, long toInclusive) {
        long actualLong = Long.parseLong((String) actual);
        assertThat(actualLong, greaterThanOrEqualTo(fromInclusive));
        assertThat(actualLong, lessThanOrEqualTo(toInclusive));
    }

    private static final class TestMachineStateStorage implements MachineStateStorage {
        private final Map<String, MachineState> states = new HashMap<>();

        @Override
        public MachineState get(String namespace, InstanceId instanceId) {
            return states.getOrDefault(key(namespace, instanceId), MachineState.NOT_FOUND);
        }

        @Override
        public void set(String namespace, int machineId, InstanceId instanceId) {
            put(namespace, instanceId, MachineState.of(machineId));
        }

        @Override
        public void remove(String namespace, InstanceId instanceId) {
            states.remove(key(namespace, instanceId));
        }

        @Override
        public void clear(String namespace) {
            states.keySet().removeIf(key -> key.startsWith(namespace + "|"));
        }

        @Override
        public int size(String namespace) {
            return (int) states.keySet().stream().filter(key -> key.startsWith(namespace + "|")).count();
        }

        @Override
        public boolean exists(String namespace, InstanceId instanceId) {
            return states.containsKey(key(namespace, instanceId));
        }

        void put(String namespace, InstanceId instanceId, MachineState machineState) {
            states.put(key(namespace, instanceId), machineState);
        }

        private static String key(String namespace, InstanceId instanceId) {
            return namespace + "|" + instanceId;
        }
    }
}
