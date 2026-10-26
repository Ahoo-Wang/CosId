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

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InMemoryMachineStateStorage;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.machine.lease.MachineLeaseManager;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.util.CallTimeout;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Lease renewals, releases and segment fetches give up after the call timeout while ZooKeeper does not answer.
 */
class ZookeeperCallTimeoutTest {
    private static final Duration SAFE_GUARD_DURATION = Duration.ofMinutes(1);
    private static final CallTimeout CALL_TIMEOUT = CallTimeout.of(Duration.ofMillis(500));
    private static final CallTimeout SETUP_CALL_TIMEOUT = CallTimeout.of(Duration.ofSeconds(30));
    
    @Test
    void leaseRenewAndReleaseGiveUpWhenZookeeperIsDown() throws Exception {
        TestingServer testingServer = new TestingServer();
        // Long connection timeout and many retries: without the call timeout, a renewal would block for minutes.
        CuratorFramework curatorFramework = CuratorFrameworkFactory.builder()
            .connectString(testingServer.getConnectString())
            .connectionTimeoutMs((int) Duration.ofSeconds(30).toMillis())
            .sessionTimeoutMs((int) Duration.ofMinutes(1).toMillis())
            .retryPolicy(new RetryNTimes(10, 1000))
            .build();
        curatorFramework.start();
        try {
            Assertions.assertTrue(curatorFramework.blockUntilConnected(10, TimeUnit.SECONDS));
            // The first claim against a fresh server can take longer than the short timeout on a slow machine: distribute
            // with a generous one. Guard and revert, after the server stops, go through the store with the short timeout.
            LeaseStoreMachineIdDistributor distributor = new LeaseStoreMachineIdDistributor(
                new MachineLeaseManager(new ZookeeperMachineLeaseStore(curatorFramework, SETUP_CALL_TIMEOUT)),
                new MachineLeaseManager(new ZookeeperMachineLeaseStore(curatorFramework, CALL_TIMEOUT)),
                new InMemoryMachineStateStorage(), ClockBackwardsSynchronizer.DEFAULT);
            String namespace = MockIdGenerator.usePrefix("timeout").generateAsString();
            InstanceId instanceId = InstanceId.of("timeout", false);
            distributor.distribute(namespace, 1, instanceId, SAFE_GUARD_DURATION);
            
            testingServer.stop();
            
            long startedAt = System.nanoTime();
            Assertions.assertThrows(CosIdException.class, () -> distributor.guard(namespace, instanceId, SAFE_GUARD_DURATION));
            Assertions.assertThrows(CosIdException.class, () -> distributor.revert(namespace, instanceId));
            Assertions.assertTrue(Duration.ofNanos(System.nanoTime() - startedAt).compareTo(Duration.ofSeconds(10)) < 0);
        } finally {
            curatorFramework.close();
            testingServer.close();
        }
    }
    
    @Test
    void segmentNextMaxIdGivesUpWhenZookeeperIsDown() throws Exception {
        TestingServer testingServer = new TestingServer();
        CuratorFramework curatorFramework = CuratorFrameworkFactory.builder()
            .connectString(testingServer.getConnectString())
            .connectionTimeoutMs((int) Duration.ofSeconds(30).toMillis())
            .sessionTimeoutMs((int) Duration.ofMinutes(1).toMillis())
            .retryPolicy(new RetryNTimes(10, 1000))
            .build();
        curatorFramework.start();
        try {
            Assertions.assertTrue(curatorFramework.blockUntilConnected(10, TimeUnit.SECONDS));
            IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition("timeout", MockIdGenerator.INSTANCE.generateAsString(), 0, 10);
            // The first fetch against a fresh server can take longer than the short timeout on a slow machine: fetch it
            // with a generous one, on the same counter. Only the fetch after the server stops uses the short timeout.
            new ZookeeperIdSegmentDistributorFactory(curatorFramework, new RetryNTimes(10, 1000), SETUP_CALL_TIMEOUT)
                .create(definition).nextMaxId();
            ZookeeperIdSegmentDistributor distributor = (ZookeeperIdSegmentDistributor) new ZookeeperIdSegmentDistributorFactory(curatorFramework,
                new RetryNTimes(10, 1000), CALL_TIMEOUT)
                .create(definition);
            
            testingServer.stop();
            
            long startedAt = System.nanoTime();
            Assertions.assertThrows(CosIdException.class, distributor::nextMaxId);
            Assertions.assertTrue(Duration.ofNanos(System.nanoTime() - startedAt).compareTo(Duration.ofSeconds(10)) < 0);
        } finally {
            curatorFramework.close();
            testingServer.close();
        }
    }
}
