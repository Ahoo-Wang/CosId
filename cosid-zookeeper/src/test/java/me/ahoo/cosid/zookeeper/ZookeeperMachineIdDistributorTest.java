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

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InMemoryMachineStateStorage;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.test.Assert;
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.test.machine.distributor.MachineIdDistributorSpec;

import lombok.SneakyThrows;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZookeeperMachineIdDistributorTest extends MachineIdDistributorSpec {
    RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3, 3000);
    CuratorFramework curatorFramework;
    TestingServer testingServer;
    MachineStateStorage machineStateStorage;
    
    @SneakyThrows
    @BeforeAll
    void setup() {
        testingServer = new TestingServer();
        curatorFramework = CuratorFrameworkFactory.newClient(testingServer.getConnectString(), new RetryNTimes(1, 10));
        curatorFramework.start();
        if (!curatorFramework.blockUntilConnected(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("CuratorFramework did not connect to TestingServer: " + testingServer.getConnectString());
        }
    }

    @BeforeEach
    void setupMachineStateStorage() {
        machineStateStorage = new InMemoryMachineStateStorage();
    }
    
    @SneakyThrows
    @AfterAll
    void destroy() {
        if (Objects.nonNull(curatorFramework)) {
            curatorFramework.close();
        }
        if (Objects.nonNull(testingServer)) {
            testingServer.close();
        }
    }
    
    @Override
    protected MachineIdDistributor getDistributor() {
        return new ZookeeperMachineIdDistributor(curatorFramework, retryPolicy, machineStateStorage, ClockBackwardsSynchronizer.DEFAULT);
    }

    @Test
    @Override
    public void guardLost() {
        MachineIdDistributor distributor = getDistributor();
        String namespace = MockIdGenerator.usePrefix("GuardLost").generateAsString();
        InstanceId instanceId = mockInstance(0, false);
        machineStateStorage.set(namespace, getMachineBit(), instanceId);

        Assert.assertThrows(MachineIdLostException.class, () -> {
            distributor.guard(namespace, instanceId, MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION);
        });
    }

}
