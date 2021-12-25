/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

import lombok.SneakyThrows;
import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.machine.InstanceId;
import me.ahoo.cosid.snowflake.machine.MachineIdOverflowException;
import me.ahoo.cosid.snowflake.machine.MachineStateStorage;
import me.ahoo.cosid.util.MockIdGenerator;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryNTimes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Objects;

/**
 * @author ahoo wang
 */
class ZookeeperMachineIdDistributorTest {
    CuratorFramework curatorFramework;
    ZookeeperMachineIdDistributor zookeeperMachineIdDistributor;

    @BeforeEach
    void init() {
        curatorFramework = CuratorFrameworkFactory.newClient("localhost:2181", new RetryNTimes(1, 10));
        curatorFramework.start();
        zookeeperMachineIdDistributor = new ZookeeperMachineIdDistributor(curatorFramework, MachineStateStorage.LOCAL, ClockBackwardsSynchronizer.DEFAULT);
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

    @AfterEach
    void destroy() {
        if (Objects.nonNull(curatorFramework)) {
            curatorFramework.close();
        }
    }
}
