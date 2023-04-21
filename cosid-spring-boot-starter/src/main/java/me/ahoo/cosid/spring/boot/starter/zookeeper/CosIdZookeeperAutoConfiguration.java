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

package me.ahoo.cosid.spring.boot.starter.zookeeper;

import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.zookeeper.ZookeeperIdSegmentDistributorFactory;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/**
 * CosId Zookeeper Auto Configuration.
 *
 * @author ahoo wang
 */
@Slf4j
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdZookeeperEnabled
@ConditionalOnClass(ZookeeperIdSegmentDistributorFactory.class)
@EnableConfigurationProperties(CosIdZookeeperProperties.class)
public class CosIdZookeeperAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RetryPolicy cosIdRetryPolicy(CosIdZookeeperProperties zookeeperProperties) {
        CosIdZookeeperProperties.Retry retry = zookeeperProperties.getRetry();
        return new ExponentialBackoffRetry(retry.getBaseSleepTimeMs(), retry.getMaxRetries(), retry.getMaxSleepMs());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public CuratorFramework cosidCuratorFramework(CosIdZookeeperProperties zookeeperProperties, RetryPolicy retryPolicy) throws InterruptedException {
        CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder();
        builder.connectString(zookeeperProperties.getConnectString());
        builder.sessionTimeoutMs((int) zookeeperProperties.getSessionTimeout().toMillis())
            .connectionTimeoutMs((int) zookeeperProperties.getConnectionTimeout().toMillis())
            .retryPolicy(retryPolicy);
        CuratorFramework curator = builder.build();
        curator.start();
        curator.blockUntilConnected((int) zookeeperProperties.getBlockUntilConnectedWait().toMillis(), TimeUnit.MILLISECONDS);
        return curator;
    }
}
