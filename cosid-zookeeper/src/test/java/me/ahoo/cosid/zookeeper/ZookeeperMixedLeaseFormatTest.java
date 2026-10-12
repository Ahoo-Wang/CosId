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

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InMemoryMachineStateStorage;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.test.machine.lease.MixedLeaseFormatSpec;
import me.ahoo.cosid.util.CallTimeout;

import lombok.SneakyThrows;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Runs {@link MixedLeaseFormatSpec} on a ZooKeeper test server: {@link ZookeeperMachineIdDistributor} as the 3.4
 * instances.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZookeeperMixedLeaseFormatTest extends MixedLeaseFormatSpec {
    TestingServer testingServer;
    CuratorFramework curatorFramework;
    ZookeeperMachineLeaseStore store;
    
    @SneakyThrows
    @BeforeAll
    void setup() {
        testingServer = new TestingServer();
        curatorFramework = CuratorFrameworkFactory.newClient(testingServer.getConnectString(), new RetryNTimes(1, 10));
        curatorFramework.start();
        if (!curatorFramework.blockUntilConnected(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("CuratorFramework did not connect to TestingServer: " + testingServer.getConnectString());
        }
        store = new ZookeeperMachineLeaseStore(curatorFramework, CallTimeout.of(Duration.ofSeconds(10)));
    }
    
    @SneakyThrows
    @AfterAll
    void destroy() {
        if (curatorFramework != null) {
            curatorFramework.close();
        }
        if (testingServer != null) {
            testingServer.close();
        }
    }
    
    @Override
    protected MachineIdDistributor v34() {
        return new ZookeeperMachineIdDistributor(curatorFramework, new RetryNTimes(1, 10), new InMemoryMachineStateStorage(),
            ClockBackwardsSynchronizer.DEFAULT);
    }
    
    @Override
    protected MachineIdDistributor v40() {
        return new LeaseStoreMachineIdDistributor(store, new InMemoryMachineStateStorage(), ClockBackwardsSynchronizer.DEFAULT);
    }
    
    @SneakyThrows
    @Override
    protected void takeReleasedAs34WithTheSameTimestamp(String namespace, int machineId, String instanceId) {
        String root = "/" + CosId.COSID + "/" + namespace;
        String revertPath = root + "/__revert/" + machineId;
        byte[] data = curatorFramework.getData().forPath(revertPath);
        curatorFramework.transaction().forOperations(
            curatorFramework.transactionOp().delete().forPath(revertPath),
            curatorFramework.transactionOp().create().forPath(root + "/__itc_idx/" + instanceId, data));
    }
}
