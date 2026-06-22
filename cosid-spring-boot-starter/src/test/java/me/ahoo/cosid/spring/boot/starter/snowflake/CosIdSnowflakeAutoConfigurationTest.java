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
import static org.mockito.Mockito.mock;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.GuardDistribute;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
                    .hasSingleBean(SnowflakeIdBeanRegistrar.class);
                assertThat(context.getBean(SnowflakeIdProperties.class).getShare().isEnabled()).isFalse();
            });
    }

    @Test
    void appliesCustomizerBeforeRegistrarRuns() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSnowflakeEnabled.ENABLED_KEY + "=true")
            .withBean(CustomizeSnowflakeIdProperties.class, () -> properties -> properties.setZoneId("UTC"))
            .run(context -> assertThat(context.getBean(SnowflakeIdProperties.class).getZoneId()).isEqualTo("UTC"));
    }

    @Test
    void doesNotCreateSnowflakeBeansWhenCosIdIsDisabled() {
        this.contextRunner
            .withPropertyValues("cosid.enabled=false")
            .withPropertyValues(ConditionalOnCosIdSnowflakeEnabled.ENABLED_KEY + "=true")
            .run(context -> assertThat(context)
                .doesNotHaveBean(SnowflakeIdProperties.class)
                .doesNotHaveBean(SnowflakeIdBeanRegistrar.class));
    }

    @Test
    void doesNotCreateSnowflakeBeansWhenSnowflakeIsDisabledOrMissing() {
        this.contextRunner
            .run(context -> assertThat(context)
                .doesNotHaveBean(SnowflakeIdProperties.class)
                .doesNotHaveBean(SnowflakeIdBeanRegistrar.class));
    }
}
