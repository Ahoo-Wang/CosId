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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.test.segment.distributor.IdSegmentDistributorSpec;

import lombok.SneakyThrows;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZookeeperIdSegmentDistributorTest extends IdSegmentDistributorSpec {
    CuratorFramework curatorFramework;
    RetryPolicy retryPolicy;
    ZookeeperIdSegmentDistributorFactory distributorFactory;
    TestingServer testingServer;
    
    @SneakyThrows
    @BeforeAll
    void setup() {
        testingServer = new TestingServer();
        retryPolicy = new ExponentialBackoffRetry(1000, 3, 3000);
        curatorFramework = CuratorFrameworkFactory.newClient(testingServer.getConnectString(), retryPolicy);
        curatorFramework.start();
        if (!curatorFramework.blockUntilConnected(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("CuratorFramework did not connect to TestingServer: " + testingServer.getConnectString());
        }
        
        distributorFactory = new ZookeeperIdSegmentDistributorFactory(curatorFramework, retryPolicy);
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
    protected IdSegmentDistributorFactory getFactory() {
        return distributorFactory;
    }
    
    @Override
    @SneakyThrows
    protected <T extends IdSegmentDistributor> void setMaxIdBack(T distributor, long maxId) {
        String counterPath = "/" + CosId.COSID + "/" + distributor.getNamespacedName();
        new DistributedAtomicLong(curatorFramework, counterPath, retryPolicy).forceSet(maxId);
    }
    
    @Override
    public void nextMaxIdWhenBack() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "nextMaxIdWhenBack", TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = factory().create(definition);
        long firstMaxId = distributor.nextMaxId();
        assertThat(firstMaxId, equalTo(TEST_OFFSET + TEST_STEP));

        setMaxIdBack(distributor, TEST_OFFSET);
        long nextMaxId = distributor.nextMaxId();

        assertThat(nextMaxId, equalTo(TEST_OFFSET + TEST_STEP));
    }
}
