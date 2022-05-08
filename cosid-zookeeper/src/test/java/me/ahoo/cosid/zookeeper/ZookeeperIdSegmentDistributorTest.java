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

import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.test.segment.distributor.IdSegmentDistributorSpec;

import lombok.SneakyThrows;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.Objects;

/**
 * @author ahoo wang
 */
class ZookeeperIdSegmentDistributorTest extends IdSegmentDistributorSpec {
    CuratorFramework curatorFramework;
    RetryPolicy retryPolicy;
    ZookeeperIdSegmentDistributorFactory distributorFactory;
    TestingServer testingServer;
    
    @SneakyThrows
    @BeforeEach
    void setup() {
        testingServer = new TestingServer();
        testingServer.start();
        retryPolicy = new ExponentialBackoffRetry(1000, 3, 3000);
        curatorFramework = CuratorFrameworkFactory.newClient(testingServer.getConnectString(), retryPolicy);
        curatorFramework.start();
        
        distributorFactory = new ZookeeperIdSegmentDistributorFactory(curatorFramework, retryPolicy);
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
    
    @Override
    protected IdSegmentDistributorFactory getFactory() {
        return distributorFactory;
    }
    
    @Override
    protected <T extends IdSegmentDistributor> void setMaxIdBack(T distributor, long maxId) {
    
    }
    
    @Override
    public void nextMaxIdWhenBack() {
        //TODO
    }
}
