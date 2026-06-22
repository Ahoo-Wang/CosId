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

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.spring.redis.SpringRedisMachineIdDistributor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * CosIdSpringRedisMachineIdDistributorAutoConfigurationTest .
 *
 * @author ahoo wang
 */
class CosIdSpringRedisMachineIdDistributorAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdSpringRedisMachineIdDistributorAutoConfiguration.class))
        .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
        .withBean(MachineStateStorage.class, () -> MachineStateStorage.IN_MEMORY)
        .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT);
    
    @Test
    void createsRedisMachineIdDistributorWithoutConnectingToRedis() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=redis")
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdSpringRedisMachineIdDistributorAutoConfiguration.class)
                    .hasSingleBean(SpringRedisMachineIdDistributor.class)
                ;
            });
    }

    @Test
    void backsOffWhenUserProvidesMachineIdDistributor() {
        SpringRedisMachineIdDistributor userDistributor = mock(SpringRedisMachineIdDistributor.class);

        this.contextRunner
            .withBean(SpringRedisMachineIdDistributor.class, () -> userDistributor)
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=redis")
            .run(context -> assertThat(context)
                .hasSingleBean(SpringRedisMachineIdDistributor.class)
                .getBean(SpringRedisMachineIdDistributor.class)
                .isSameAs(userDistributor));
    }

    @Test
    void doesNotCreateDistributorWhenMachineIsDisabled() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=false")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=redis")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdSpringRedisMachineIdDistributorAutoConfiguration.class)
                .doesNotHaveBean(SpringRedisMachineIdDistributor.class));
    }

    @Test
    void doesNotCreateDistributorWhenTypeDoesNotMatch() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=jdbc")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdSpringRedisMachineIdDistributorAutoConfiguration.class)
                .doesNotHaveBean(SpringRedisMachineIdDistributor.class));
    }

    @Test
    void doesNotCreateDistributorWhenRedisDistributorClassIsMissing() {
        this.contextRunner
            .withClassLoader(new FilteredClassLoader(SpringRedisMachineIdDistributor.class))
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=redis")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdSpringRedisMachineIdDistributorAutoConfiguration.class)
                .doesNotHaveBean(SpringRedisMachineIdDistributor.class));
    }
}
