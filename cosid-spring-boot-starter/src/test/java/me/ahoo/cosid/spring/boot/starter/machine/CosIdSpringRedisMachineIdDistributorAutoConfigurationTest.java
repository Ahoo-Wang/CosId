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
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.spring.redis.SpringRedisMachineLeaseStore;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

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
                    .hasSingleBean(LeaseStoreMachineIdDistributor.class)
                ;
            });
    }

    @Test
    void wiresRedisTimeoutProperty() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=redis")
            .run(context -> assertThat(context.getBean(SpringRedisMachineLeaseStore.class).getCommandTimeout().getTimeout())
                .isEqualTo(Duration.ofSeconds(1)));
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=redis")
            .withPropertyValues("cosid.machine.distributor.redis.timeout=3s")
            .run(context -> assertThat(context.getBean(SpringRedisMachineLeaseStore.class).getCommandTimeout().getTimeout())
                .isEqualTo(Duration.ofSeconds(3)));
    }
    
    @Test
    void zeroRedisTimeoutFailsStartupNamingTheProperty() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=redis")
            .withPropertyValues("cosid.machine.distributor.redis.timeout=0")
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessageContaining("cosid.machine.distributor.redis.timeout"));
    }

    @Test
    void backsOffWhenUserProvidesMachineIdDistributor() {
        MachineIdDistributor userDistributor = mock(MachineIdDistributor.class);

        this.contextRunner
            .withBean(MachineIdDistributor.class, () -> userDistributor)
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=redis")
            .run(context -> assertThat(context)
                .hasSingleBean(MachineIdDistributor.class)
                .getBean(MachineIdDistributor.class)
                .isSameAs(userDistributor));
    }

    @Test
    void doesNotCreateDistributorWhenMachineIsDisabled() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=false")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=redis")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdSpringRedisMachineIdDistributorAutoConfiguration.class)
                .doesNotHaveBean(LeaseStoreMachineIdDistributor.class));
    }

    @Test
    void doesNotCreateDistributorWhenTypeDoesNotMatch() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=jdbc")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdSpringRedisMachineIdDistributorAutoConfiguration.class)
                .doesNotHaveBean(LeaseStoreMachineIdDistributor.class));
    }

    @Test
    void doesNotCreateDistributorWhenRedisDistributorClassIsMissing() {
        this.contextRunner
            .withClassLoader(new FilteredClassLoader(SpringRedisMachineLeaseStore.class))
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=redis")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdSpringRedisMachineIdDistributorAutoConfiguration.class)
                .doesNotHaveBean(LeaseStoreMachineIdDistributor.class));
    }
}
