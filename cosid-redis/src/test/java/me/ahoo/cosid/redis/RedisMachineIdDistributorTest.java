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

package me.ahoo.cosid.redis;

import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.machine.InstanceId;
import me.ahoo.cosid.snowflake.machine.MachineIdLostException;
import me.ahoo.cosid.snowflake.machine.MachineStateStorage;
import me.ahoo.cosid.snowflake.machine.MachineIdOverflowException;
import me.ahoo.cosid.util.MockIdGenerator;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.internal.Exceptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisMachineIdDistributorTest {
    protected RedisClient redisClient;
    protected StatefulRedisConnection<String, String> redisConnection;
    protected RedisMachineIdDistributor redisMachineIdDistributor;

    @BeforeAll
    void initRedis() {
        System.out.println("--- initRedis ---");
        redisClient = RedisClient.create("redis://localhost:6379");
        redisConnection = redisClient.connect();
        redisMachineIdDistributor = new RedisMachineIdDistributor(redisConnection.reactive(), MachineStateStorage.LOCAL, ClockBackwardsSynchronizer.DEFAULT);
    }

    @Test
    void distribute() {
        int machineBit = 1;
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        InstanceId instanceId = InstanceId.of("127.0.0.1", 80, false);
        int machineId = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId);
        Assertions.assertEquals(0, machineId);

        machineId = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId);
        Assertions.assertEquals(0, machineId);

        InstanceId instanceId1 = InstanceId.of("127.0.0.1", 82, false);
        int machineId1 = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId1);
        Assertions.assertEquals(1, machineId1);

        Throwable machineIdOverflowException = null;
        try {
            InstanceId instanceId2 = InstanceId.of("127.0.0.1", 83, false);
            int machineId2 = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId2);
            Assertions.assertEquals(-1, machineId2);
        } catch (Throwable executionException) {
            machineIdOverflowException = Exceptions.unwrap(executionException);
        }

        Assertions.assertTrue(machineIdOverflowException instanceof MachineIdOverflowException);

        redisMachineIdDistributor.revert(namespace, instanceId);
        InstanceId instanceId3 = InstanceId.of("127.0.0.1", 84, false);
        int machineId3 = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId3);
        Assertions.assertEquals(0, machineId3);
    }

    @Test
    void distributeWhenStable() {
        int machineBit = 1;
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        InstanceId instanceId = InstanceId.of("127.0.0.1", 80, true);
        int machineId = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId);
        Assertions.assertEquals(0, machineId);

        machineId = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId);
        Assertions.assertEquals(0, machineId);

        InstanceId instanceId1 = InstanceId.of("127.0.0.1", 82, true);
        int machineId1 = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId1);
        Assertions.assertEquals(1, machineId1);

        Throwable machineIdOverflowException = null;
        try {
            InstanceId instanceId2 = InstanceId.of("127.0.0.1", 83, true);
            int machineId2 = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId2);
            Assertions.assertEquals(-1, machineId2);
        } catch (Throwable executionException) {
            machineIdOverflowException = Exceptions.unwrap(executionException);
        }
        Assertions.assertTrue(machineIdOverflowException instanceof MachineIdOverflowException);

        redisMachineIdDistributor.revert(namespace, instanceId);
        try {
            InstanceId instanceId3 = InstanceId.of("127.0.0.1", 84, true);
            int machineId3 = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId3);
            Assertions.assertEquals(-1, machineId3);
        } catch (Throwable executionException) {
            machineIdOverflowException = Exceptions.unwrap(executionException);
        }
        Assertions.assertTrue(machineIdOverflowException instanceof MachineIdOverflowException);

        machineId = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId);
        Assertions.assertEquals(0, machineId);
    }
    @Test
    void guard() {
        int machineBit = 1;
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        InstanceId instanceId = InstanceId.of("127.0.0.1", 80, false);
        int machineId = redisMachineIdDistributor.distribute(namespace, machineBit, instanceId);
        Assertions.assertEquals(0, machineId);
        redisMachineIdDistributor.guard(namespace, instanceId);
    }
    @Test
    void guardWhenMachineIdLost() {
        int machineBit = 1;
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        InstanceId instanceId = InstanceId.of("127.0.0.1", 80, false);
        MachineStateStorage.LOCAL.set(namespace, machineBit, instanceId);
        Assertions.assertThrows(MachineIdLostException.class,()-> redisMachineIdDistributor.guard(namespace, instanceId));
    }

    @AfterAll
    void destroyRedis() {
        System.out.println("--- destroyRedis ---");

        if (Objects.nonNull(redisConnection)) {
            redisConnection.close();
        }
        if (Objects.nonNull(redisClient)) {
            redisClient.shutdown();
        }
    }
}
