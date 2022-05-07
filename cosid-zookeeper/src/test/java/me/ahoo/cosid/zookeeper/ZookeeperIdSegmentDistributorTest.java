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

import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.SegmentId;
import me.ahoo.cosid.test.ConcurrentGenerateTest;
import me.ahoo.cosid.util.MockIdGenerator;

import lombok.SneakyThrows;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
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
class ZookeeperIdSegmentDistributorTest {
    CuratorFramework curatorFramework;
    RetryPolicy retryPolicy;
    ZookeeperIdSegmentDistributor zookeeperIdSegmentDistributor;
    TestingServer testingServer;
    
    @SneakyThrows
    @BeforeEach
    void setup() {
        testingServer = new TestingServer();
        testingServer.start();
        retryPolicy = new ExponentialBackoffRetry(1000, 3, 3000);
        curatorFramework = CuratorFrameworkFactory.newClient(testingServer.getConnectString(), retryPolicy);
        curatorFramework.start();
        
        ZookeeperIdSegmentDistributorFactory distributorFactory = new ZookeeperIdSegmentDistributorFactory(curatorFramework, retryPolicy);
        zookeeperIdSegmentDistributor = (ZookeeperIdSegmentDistributor)distributorFactory.create(new IdSegmentDistributorDefinition("ZookeeperIdSegmentDistributorTest", MockIdGenerator.INSTANCE.generateAsString(),IdSegmentDistributor.DEFAULT_OFFSET, IdSegmentDistributor.DEFAULT_STEP));
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
    
    @Test
    void nextMaxId() {
        long nextMaxId = zookeeperIdSegmentDistributor.nextMaxId(100);
        Assertions.assertEquals(100, nextMaxId);
        nextMaxId = zookeeperIdSegmentDistributor.nextMaxId(100);
        Assertions.assertEquals(200, nextMaxId);
    }
    
    @Test
    void nextMaxIdOffset() {
        ZookeeperIdSegmentDistributor distributorOffset100 =
            new ZookeeperIdSegmentDistributor("ZookeeperIdSegmentDistributorTest", MockIdGenerator.INSTANCE.generateAsString(), 100, IdSegmentDistributor.DEFAULT_STEP, curatorFramework, retryPolicy);
        long nextMaxId = distributorOffset100.nextMaxId(100);
        Assertions.assertEquals(200, nextMaxId);
        nextMaxId = distributorOffset100.nextMaxId(100);
        Assertions.assertEquals(300, nextMaxId);
    }
    
    @Test
    void nextMaxIdConcurrent() {
        int times = 100;
        CompletableFuture<Long>[] results = new CompletableFuture[100];
        ZookeeperIdSegmentDistributor distributor =
            new ZookeeperIdSegmentDistributor("ZookeeperIdSegmentDistributorTest", MockIdGenerator.INSTANCE.generateAsString(), 0, IdSegmentDistributor.DEFAULT_STEP, curatorFramework, retryPolicy);
        
        for (int i = 0; i < times; i++) {
            results[i] = CompletableFuture.supplyAsync(() -> distributor.nextMaxId(1));
        }
        
        CompletableFuture.allOf(results).join();
        
        Long[] machineIds = Arrays.stream(results).map(CompletableFuture::join).sorted().toArray(Long[]::new);
        for (int i = 0; i < machineIds.length; i++) {
            Assertions.assertEquals(i + 1, machineIds[i]);
        }
    }
    
    @Test
    public void generateWhenConcurrent() {
        ZookeeperIdSegmentDistributor distributor =
            new ZookeeperIdSegmentDistributor("generateWhenConcurrent", MockIdGenerator.INSTANCE.generateAsString(), 0, IdSegmentDistributor.DEFAULT_STEP, curatorFramework, retryPolicy);
        
        SegmentId defaultSegmentId = new DefaultSegmentId(distributor);
        new ConcurrentGenerateTest(10, 800000, defaultSegmentId).assertConcurrentGenerate();
    }
    
    @Test
    public void generateWhenMultiInstanceConcurrent() {
        ZookeeperIdSegmentDistributor distributor =
            new ZookeeperIdSegmentDistributor("generateWhenConcurrent", MockIdGenerator.INSTANCE.generateAsString(), 0, IdSegmentDistributor.DEFAULT_STEP, curatorFramework, retryPolicy);
        
        SegmentId idGenerator1 = new DefaultSegmentId(distributor);
        SegmentId idGenerator2 = new DefaultSegmentId(distributor);
        new ConcurrentGenerateTest(10, 800000, idGenerator1, idGenerator2).assertConcurrentGenerate();
    }
    
}
