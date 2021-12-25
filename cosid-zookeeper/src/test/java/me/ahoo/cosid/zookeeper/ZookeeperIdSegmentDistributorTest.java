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

import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.util.MockIdGenerator;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.retry.RetryNTimes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Objects;

/**
 * @author ahoo wang
 */
class ZookeeperIdSegmentDistributorTest {
    CuratorFramework curatorFramework;
    RetryPolicy retryPolicy;
    ZookeeperIdSegmentDistributor zookeeperIdSegmentDistributor;

    @BeforeEach
    void init() {
        retryPolicy = new ExponentialBackoffRetry(1000, 3, 3000);
        curatorFramework = CuratorFrameworkFactory.newClient("localhost:2181", retryPolicy);
        curatorFramework.start();
        zookeeperIdSegmentDistributor = new ZookeeperIdSegmentDistributor(MockIdGenerator.INSTANCE.generateAsString(), MockIdGenerator.INSTANCE.generateAsString(), IdSegmentDistributor.DEFAULT_OFFSET, IdSegmentDistributor.DEFAULT_STEP, curatorFramework, retryPolicy);
    }

    @Test
    void nextMaxId() {
        long nextMaxID = zookeeperIdSegmentDistributor.nextMaxId(100);
        Assertions.assertEquals(100, nextMaxID);
        nextMaxID = zookeeperIdSegmentDistributor.nextMaxId(100);
        Assertions.assertEquals(200, nextMaxID);
    }

    @Test
    void nextMaxIdOffset() {
        ZookeeperIdSegmentDistributor distributorOffset100 = new ZookeeperIdSegmentDistributor("dev", MockIdGenerator.INSTANCE.generateAsString(), 100, IdSegmentDistributor.DEFAULT_STEP, curatorFramework, retryPolicy);
        long nextMaxID = distributorOffset100.nextMaxId(100);
        Assertions.assertEquals(200, nextMaxID);
        nextMaxID = distributorOffset100.nextMaxId(100);
        Assertions.assertEquals(300, nextMaxID);
    }

    @AfterEach
    void destroy() {
        if (Objects.nonNull(curatorFramework)) {
            curatorFramework.close();
        }
    }
}
