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

package me.ahoo.cosid.spring.boot.starter.machine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.zookeeper.ZookeeperMachineIdDistributor;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CosIdZookeeperMachineIdDistributorAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdZookeeperMachineIdDistributorAutoConfiguration.class))
        .withBean(CuratorFramework.class, () -> mock(CuratorFramework.class))
        .withBean(RetryPolicy.class, () -> mock(RetryPolicy.class))
        .withBean(MachineStateStorage.class, () -> MachineStateStorage.IN_MEMORY)
        .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT);

    @Test
    void createsZookeeperMachineDistributorWithoutConnectingToZookeeper() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=zookeeper")
            .run(context -> assertThat(context)
                .hasSingleBean(CosIdZookeeperMachineIdDistributorAutoConfiguration.class)
                .hasSingleBean(ZookeeperMachineIdDistributor.class));
    }

    @Test
    void backsOffWhenUserProvidesZookeeperMachineDistributor() {
        ZookeeperMachineIdDistributor distributor = mock(ZookeeperMachineIdDistributor.class);

        this.contextRunner
            .withBean(ZookeeperMachineIdDistributor.class, () -> distributor)
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=zookeeper")
            .run(context -> assertThat(context.getBean(ZookeeperMachineIdDistributor.class)).isSameAs(distributor));
    }

    @Test
    void doesNotCreateDistributorWhenMachineIsDisabled() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=false")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=zookeeper")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdZookeeperMachineIdDistributorAutoConfiguration.class)
                .doesNotHaveBean(ZookeeperMachineIdDistributor.class));
    }

    @Test
    void doesNotCreateDistributorWhenTypeDoesNotMatch() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=redis")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdZookeeperMachineIdDistributorAutoConfiguration.class)
                .doesNotHaveBean(ZookeeperMachineIdDistributor.class));
    }

    @Test
    void doesNotCreateDistributorWhenZookeeperMachineClassIsMissing() {
        this.contextRunner
            .withClassLoader(new FilteredClassLoader(ZookeeperMachineIdDistributor.class))
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=zookeeper")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdZookeeperMachineIdDistributorAutoConfiguration.class)
                .doesNotHaveBean(ZookeeperMachineIdDistributor.class));
    }
}
