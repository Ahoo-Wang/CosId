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

package me.ahoo.cosid.spring.boot.starter.snowflake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.Decorator;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.GuardDistribute;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdLease;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.snowflake.ClockSyncSnowflakeId;
import me.ahoo.cosid.snowflake.LeasedSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeId;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.ArrayList;
import java.util.List;

class CosIdSnowflakeAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdAutoConfiguration.class, CosIdSnowflakeAutoConfiguration.class))
        .withBean(MachineProperties.class, MachineProperties::new)
        .withBean(InstanceId.class, () -> InstanceId.of("test-instance", true))
        .withBean(GuardDistribute.class, () -> mock(GuardDistribute.class))
        .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT)
        .withPropertyValues(SnowflakeIdProperties.PREFIX + ".share.enabled=false");

    @Test
    void createsRegistrarAndBindsPropertiesWhenSnowflakeIsEnabled() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSnowflakeEnabled.ENABLED_KEY + "=true")
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(SnowflakeIdProperties.class)
                    .hasSingleBean(IdGeneratorProvider.class)
                    .hasSingleBean(SnowflakeIdBeanDefinitionRegistrar.class);
                assertThat(context.getBean(SnowflakeIdProperties.class).getShare().isEnabled()).isFalse();
            });
    }

    @Test
    void appliesCustomizerBeforeRegistrarRuns() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSnowflakeEnabled.ENABLED_KEY + "=true")
            .withBean(CustomizeSnowflakeIdProperties.class, () -> properties -> properties.setZoneId(java.time.ZoneId.of("UTC")))
            .run(context -> assertThat(context.getBean(SnowflakeIdProperties.class).getZoneId()).isEqualTo(java.time.ZoneId.of("UTC")));
    }

    @Test
    void doesNotCreateSnowflakeBeansWhenCosIdIsDisabled() {
        this.contextRunner
            .withPropertyValues("cosid.enabled=false")
            .withPropertyValues(ConditionalOnCosIdSnowflakeEnabled.ENABLED_KEY + "=true")
            .run(context -> assertThat(context)
                .doesNotHaveBean(SnowflakeIdProperties.class)
                .doesNotHaveBean(SnowflakeIdBeanDefinitionRegistrar.class));
    }

    @Test
    void doesNotCreateSnowflakeBeansWhenSnowflakeIsDisabledOrMissing() {
        this.contextRunner
            .run(context -> assertThat(context)
                .doesNotHaveBean(SnowflakeIdProperties.class)
                .doesNotHaveBean(SnowflakeIdBeanDefinitionRegistrar.class));
    }

    @Test
    void groupPrefixFailsAtStartup() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSnowflakeEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SnowflakeIdProperties.PREFIX + ".provider.order.converter.group-prefix.enabled=true")
            .run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("Snowflake id [order] has converter.group-prefix enabled"));
    }
    
    private ApplicationContextRunner distributingRunner() {
        GuardDistribute guardDistribute = mock(GuardDistribute.class);
        when(guardDistribute.distribute(any(), anyInt(), any(), any())).thenReturn(MachineState.of(1, 0));
        return contextRunner
            .withAllowBeanDefinitionOverriding(true)
            .withBean(MachineProperties.class, () -> {
                MachineProperties properties = new MachineProperties();
                properties.getGuarder().setFailFast(false);
                return properties;
            })
            .withBean(GuardDistribute.class, () -> guardDistribute)
            .withPropertyValues(ConditionalOnCosIdSnowflakeEnabled.ENABLED_KEY + "=true");
    }
    
    @Test
    void secondIdsWithTheDefaultEpochArePositive() {
        distributingRunner()
            .withPropertyValues(SnowflakeIdProperties.PREFIX + ".provider.sec.timestamp-unit=second")
            .run(context -> {
                SnowflakeId sec = context.getBean(IdGeneratorProvider.class).getRequired("sec", SnowflakeId.class);
                assertThat(sec.getEpoch()).isEqualTo(CosId.COSID_EPOCH_SECOND);
                for (int i = 0; i < 100; i++) {
                    assertThat(sec.generate()).isPositive();
                }
            });
    }
    
    @Test
    void secondIdsWithAnEpochInMillisecondsFailAtStartup() {
        distributingRunner()
            .withPropertyValues(SnowflakeIdProperties.PREFIX + ".provider.sec.timestamp-unit=second",
                SnowflakeIdProperties.PREFIX + ".provider.sec.epoch=1600000000000")
            .run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("Snowflake id [sec] has epoch [1600000000000] after the current time"));
    }
    
    @Test
    void millisecondIdsKeepTheDefaultEpoch() {
        distributingRunner()
            .withPropertyValues(SnowflakeIdProperties.PREFIX + ".provider.ms.sequence-bit=12")
            .run(context -> {
                SnowflakeId ms = context.getBean(IdGeneratorProvider.class).getRequired("ms", SnowflakeId.class);
                assertThat(ms.getEpoch()).isEqualTo(CosId.COSID_EPOCH);
                assertThat(ms.generate()).isPositive();
            });
    }
    
    /**
     * The lease is checked inside the clock sync, so a generation retried after waiting for the clock checks it again.
     */
    @Test
    void theLeaseIsCheckedInsideTheClockSync() {
        GuardDistribute guardDistribute = mock(GuardDistribute.class);
        when(guardDistribute.distribute(any(), anyInt(), any(), any())).thenReturn(MachineState.of(1, 0));
        when(guardDistribute.getLease(any(), any())).thenReturn(MachineIdLease.FOREVER);
        contextRunner
            .withBean(GuardDistribute.class, () -> guardDistribute)
            .withAllowBeanDefinitionOverriding(true)
            .withPropertyValues(ConditionalOnCosIdSnowflakeEnabled.ENABLED_KEY + "=true", SnowflakeIdProperties.PREFIX + ".provider.order.clock-sync=true")
            .run(context -> {
                List<Class<?>> chain = new ArrayList<>();
                Object generator = context.getBean(IdGeneratorProvider.class).getRequired("order");
                while (generator instanceof Decorator<?> decorator) {
                    chain.add(generator.getClass());
                    generator = decorator.getActual();
                }
                assertThat(chain).contains(ClockSyncSnowflakeId.class, LeasedSnowflakeId.class);
                assertThat(chain.indexOf(ClockSyncSnowflakeId.class)).isLessThan(chain.indexOf(LeasedSnowflakeId.class));
            });
    }
}
