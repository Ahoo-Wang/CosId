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

package me.ahoo.cosid.zookeeper;

import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.machine.InstanceId;
import me.ahoo.cosid.snowflake.machine.MachineIdOverflowException;
import me.ahoo.cosid.snowflake.machine.MachineStateStorage;
import me.ahoo.cosid.util.MockIdGenerator;

import lombok.SneakyThrows;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
class ZookeeperMachineIdDistributorTest {
    CuratorFramework curatorFramework;
    ZookeeperMachineIdDistributor zookeeperMachineIdDistributor;
    TestingServer testingServer;
    
    @SneakyThrows
    @BeforeEach
    void setup() {
        testingServer = new TestingServer();
        testingServer.start();
        curatorFramework = CuratorFrameworkFactory.newClient(testingServer.getConnectString(), new RetryNTimes(1, 10));
        curatorFramework.start();
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3, 3000);
        zookeeperMachineIdDistributor = new ZookeeperMachineIdDistributor(curatorFramework, retryPolicy, MachineStateStorage.LOCAL, ClockBackwardsSynchronizer.DEFAULT);
    }
    
    @SneakyThrows
    @AfterEach
    void destroy() {
        if (Objects.nonNull(curatorFramework)) {
            curatorFramework.close();
        }
        if (Objects.nonNull(testingServer)) {
            testingServer.stop();
        }
    }
    
    @SneakyThrows
    @Test
    void distribute() {
        int machineBit = 1;
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        InstanceId instanceId = InstanceId.of("127.0.0.1", 80, false);
        int machineId = zookeeperMachineIdDistributor.distribute(namespace, machineBit, instanceId);
        Assertions.assertEquals(0, machineId);
        machineId = zookeeperMachineIdDistributor.distribute(namespace, machineBit, instanceId);
        Assertions.assertEquals(0, machineId);
        
        InstanceId instanceId1 = InstanceId.of("127.0.0.1", 82, false);
        machineId = zookeeperMachineIdDistributor.distribute(namespace, machineBit, instanceId1);
        Assertions.assertEquals(1, machineId);
        
        Assertions.assertThrows(MachineIdOverflowException.class, () -> {
            InstanceId instanceId2 = InstanceId.of("127.0.0.1", 83, false);
            zookeeperMachineIdDistributor.distribute(namespace, machineBit, instanceId2);
        });
        zookeeperMachineIdDistributor.revert(namespace, instanceId);
        InstanceId instanceId3 = InstanceId.of("127.0.0.1", 84, false);
        int machineId3 = zookeeperMachineIdDistributor.distribute(namespace, machineBit, instanceId3);
        Assertions.assertEquals(0, machineId3);
    }
    
    @Test
    void distributeWhenStable() {
        int machineBit = 1;
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        InstanceId instanceId = InstanceId.of("127.0.0.1", 80, true);
        int machineId = zookeeperMachineIdDistributor.distribute(namespace, machineBit, instanceId);
        Assertions.assertEquals(0, machineId);
        machineId = zookeeperMachineIdDistributor.distribute(namespace, machineBit, instanceId);
        Assertions.assertEquals(0, machineId);
        
        InstanceId instanceId1 = InstanceId.of("127.0.0.1", 82, true);
        machineId = zookeeperMachineIdDistributor.distribute(namespace, machineBit, instanceId1);
        Assertions.assertEquals(1, machineId);
        
        Assertions.assertThrows(MachineIdOverflowException.class, () -> {
            InstanceId instanceId2 = InstanceId.of("127.0.0.1", 83, true);
            zookeeperMachineIdDistributor.distribute(namespace, machineBit, instanceId2);
        });
        
        zookeeperMachineIdDistributor.revert(namespace, instanceId);
        
        Assertions.assertThrows(MachineIdOverflowException.class, () -> {
            InstanceId instanceId3 = InstanceId.of("127.0.0.1", 84, true);
            zookeeperMachineIdDistributor.distribute(namespace, machineBit, instanceId3);
        });
        
        machineId = zookeeperMachineIdDistributor.distribute(namespace, machineBit, instanceId);
        Assertions.assertEquals(0, machineId);
    }
    
    
    @Test
    void distributeConcurrent() {
        int machineBit = 5;
        int totalMachine = ~(-1 << machineBit) + 1;
        CompletableFuture<Integer>[] results = new CompletableFuture[totalMachine];
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        
        for (int i = 0; i < totalMachine; i++) {
            results[i] = CompletableFuture.supplyAsync(() -> {
                InstanceId instanceId = InstanceId.of(MockIdGenerator.INSTANCE.generateAsString(), false);
                return zookeeperMachineIdDistributor.distribute(namespace, machineBit, instanceId);
            });
        }
        
        CompletableFuture.allOf(results).join();
        
        Integer[] machineIds = Arrays.stream(results).map(CompletableFuture::join).sorted().toArray(Integer[]::new);
        for (int i = 0; i < machineIds.length; i++) {
            Assertions.assertEquals(i, machineIds[i]);
        }
    }

}
