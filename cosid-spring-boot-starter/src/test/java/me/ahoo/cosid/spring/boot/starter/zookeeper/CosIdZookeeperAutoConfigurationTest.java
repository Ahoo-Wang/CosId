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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import me.ahoo.cosid.zookeeper.ZookeeperIdSegmentDistributorFactory;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CosIdZookeeperAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdZookeeperAutoConfiguration.class))
        .withPropertyValues("cosid.segment.distributor.type=zookeeper");

    @Test
    void createsRetryPolicyFromBoundPropertiesWithoutStartingCuratorWhenUserProvidesCurator() {
        CuratorFramework curator = mock(CuratorFramework.class);

        this.contextRunner
            .withBean(CuratorFramework.class, () -> curator)
            .withPropertyValues(
                "cosid.zookeeper.retry.base-sleep-time-ms=25",
                "cosid.zookeeper.retry.max-retries=3",
                "cosid.zookeeper.retry.max-sleep-ms=250"
            )
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdZookeeperProperties.class)
                    .hasSingleBean(RetryPolicy.class)
                    .hasSingleBean(CuratorFramework.class);
                assertThat(context.getBean(RetryPolicy.class)).isInstanceOf(ExponentialBackoffRetry.class);
                assertThat(context.getBean(CuratorFramework.class)).isSameAs(curator);
            });
    }

    @Test
    void backsOffForUserProvidedRetryPolicy() {
        RetryPolicy retryPolicy = mock(RetryPolicy.class);

        this.contextRunner
            .withBean(RetryPolicy.class, () -> retryPolicy)
            .withBean(CuratorFramework.class, () -> mock(CuratorFramework.class))
            .run(context -> assertThat(context.getBean(RetryPolicy.class)).isSameAs(retryPolicy));
    }

    @Test
    void doesNotCreateZookeeperInfrastructureWhenCosIdIsDisabled() {
        this.contextRunner
            .withPropertyValues("cosid.enabled=false")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdZookeeperProperties.class)
                .doesNotHaveBean(RetryPolicy.class)
                .doesNotHaveBean(CuratorFramework.class));
    }

    @Test
    void doesNotCreateZookeeperInfrastructureWhenZookeeperIsDisabled() {
        this.contextRunner
            .withPropertyValues("cosid.zookeeper.enabled=false")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdZookeeperProperties.class)
                .doesNotHaveBean(RetryPolicy.class)
                .doesNotHaveBean(CuratorFramework.class));
    }

    @Test
    void doesNotCreateZookeeperInfrastructureWhenCosIdZookeeperModuleIsMissing() {
        this.contextRunner
            .withClassLoader(new FilteredClassLoader(ZookeeperIdSegmentDistributorFactory.class))
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdZookeeperProperties.class)
                .doesNotHaveBean(RetryPolicy.class)
                .doesNotHaveBean(CuratorFramework.class));
    }

    @Test
    void doesNotConnectWhenNeitherDistributorIsZookeeper() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CosIdZookeeperAutoConfiguration.class))
            .withPropertyValues("cosid.machine.distributor.type=redis", "cosid.segment.distributor.type=redis")
            .run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(CosIdZookeeperProperties.class)
                .doesNotHaveBean(CuratorFramework.class));
    }

    @Test
    void createsZookeeperInfrastructureWhenTheMachineDistributorIsZookeeper() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CosIdZookeeperAutoConfiguration.class))
            .withBean(CuratorFramework.class, () -> mock(CuratorFramework.class))
            .withPropertyValues("cosid.machine.distributor.type=ZOOKEEPER")
            .run(context -> assertThat(context)
                .hasSingleBean(RetryPolicy.class)
                .hasSingleBean(CuratorFramework.class));
    }

    @Test
    void createsZookeeperInfrastructureWhenExplicitlyEnabled() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CosIdZookeeperAutoConfiguration.class))
            .withBean(CuratorFramework.class, () -> mock(CuratorFramework.class))
            .withPropertyValues("cosid.machine.distributor.type=redis", "cosid.segment.distributor.type=redis", "cosid.zookeeper.enabled=true")
            .run(context -> assertThat(context)
                .hasSingleBean(CosIdZookeeperProperties.class)
                .hasSingleBean(RetryPolicy.class)
                .hasSingleBean(CuratorFramework.class));
    }
}
