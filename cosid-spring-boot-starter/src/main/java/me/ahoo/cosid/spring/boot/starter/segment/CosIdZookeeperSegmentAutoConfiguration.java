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

package me.ahoo.cosid.spring.boot.starter.segment;

import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.store.BuiltInStoreProvider;
import me.ahoo.cosid.spring.boot.starter.store.ConditionalOnBuiltInSegmentStore;
import me.ahoo.cosid.spring.boot.starter.store.ConditionalOnSegmentStoreType;
import me.ahoo.cosid.util.CallTimeout;
import me.ahoo.cosid.zookeeper.ZookeeperIdSegmentDistributor;
import me.ahoo.cosid.zookeeper.ZookeeperIdSegmentDistributorFactory;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * CosId Zookeeper Segment AutoConfiguration.
 *
 * @author ahoo wang
 */
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdSegmentEnabled
@EnableConfigurationProperties(SegmentIdProperties.class)
@ConditionalOnClass(ZookeeperIdSegmentDistributor.class)
@ConditionalOnSegmentStoreType("zookeeper")
public class CosIdZookeeperSegmentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBuiltInSegmentStore
    public IdSegmentDistributorFactory idSegmentDistributorFactory(CuratorFramework curatorFramework, RetryPolicy retryPolicy,
                                                                   SegmentIdProperties segmentIdProperties) {
        return new ZookeeperIdSegmentDistributorFactory(curatorFramework, retryPolicy,
            CallTimeout.of(segmentIdProperties.getDistributor().getZookeeper().getTimeout()));
    }

    /**
     * The ZooKeeper backend for {@link me.ahoo.cosid.spring.boot.starter.store.CosIdStoreProvider} selection.
     */
    @Bean
    public BuiltInStoreProvider cosIdZookeeperSegmentStoreProvider(ObjectProvider<CuratorFramework> curatorFramework, ObjectProvider<RetryPolicy> retryPolicy) {
        return BuiltInStoreProvider.segment("zookeeper", context -> idSegmentDistributorFactory(curatorFramework.getObject(), retryPolicy.getObject(),
            context.getSegmentIdProperties()));
    }

}
