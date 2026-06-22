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

package me.ahoo.cosid.spring.boot.starter.cosid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.cosid.CosIdGenerator;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.GuardDistribute;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CosIdGeneratorAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdAutoConfiguration.class, CosIdGeneratorAutoConfiguration.class))
        .withBean(MachineProperties.class, MachineProperties::new)
        .withBean(InstanceId.class, () -> InstanceId.of("test-instance", true))
        .withBean(GuardDistribute.class, CosIdGeneratorAutoConfigurationTest::guardDistribute)
        .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT);

    @Test
    void createsCosIdGeneratorAndRegistersItWithProviderWhenEnabled() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdGeneratorEnabled.ENABLED_KEY + "=true")
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdGeneratorProperties.class)
                    .hasSingleBean(CosIdGenerator.class);
                assertThat(context.getBean(IdGeneratorProvider.class).get(CosId.COSID)).isPresent();
            });
    }

    @Test
    void bindsGeneratorTypeBeforeCreatingGenerator() {
        this.contextRunner
            .withPropertyValues(
                ConditionalOnCosIdGeneratorEnabled.ENABLED_KEY + "=true",
                CosIdGeneratorProperties.PREFIX + ".type=RADIX36",
                CosIdGeneratorProperties.PREFIX + ".namespace=generator-namespace"
            )
            .run(context -> {
                assertThat(context.getBean(CosIdGeneratorProperties.class).getType())
                    .isEqualTo(CosIdGeneratorProperties.Type.RADIX36);
                assertThat(context).hasSingleBean(CosIdGenerator.class);
            });
    }

    @Test
    void backsOffWhenUserProvidesCosIdGenerator() {
        CosIdGenerator generator = mock(CosIdGenerator.class);

        this.contextRunner
            .withBean(CosIdGenerator.class, () -> generator)
            .withPropertyValues(ConditionalOnCosIdGeneratorEnabled.ENABLED_KEY + "=true")
            .run(context -> assertThat(context.getBean(CosIdGenerator.class)).isSameAs(generator));
    }

    @Test
    void doesNotCreateGeneratorWhenCosIdIsDisabled() {
        this.contextRunner
            .withPropertyValues(
                "cosid.enabled=false",
                ConditionalOnCosIdGeneratorEnabled.ENABLED_KEY + "=true"
            )
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdGeneratorProperties.class)
                .doesNotHaveBean(CosIdGenerator.class));
    }

    @Test
    void doesNotCreateGeneratorWhenGeneratorIsDisabledOrMissing() {
        this.contextRunner
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdGeneratorProperties.class)
                .doesNotHaveBean(CosIdGenerator.class));
    }

    private static GuardDistribute guardDistribute() {
        GuardDistribute guardDistribute = mock(GuardDistribute.class);
        when(guardDistribute.distribute(anyString(), anyInt(), any(InstanceId.class), any()))
            .thenReturn(new MachineState(1, 0));
        return guardDistribute;
    }
}
