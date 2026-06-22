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
import me.ahoo.cosid.machine.GuardDistribute;
import me.ahoo.cosid.machine.HostAddressSupplier;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdGuarder;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.ManualMachineIdDistributor;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CosIdMachineAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdAutoConfiguration.class, CosIdMachineAutoConfiguration.class))
        .withBean(HostAddressSupplier.class, () -> () -> "10.0.0.10");

    @Test
    void createsManualMachineInfrastructureWhenMachineIsEnabled() {
        this.contextRunner
            .withPropertyValues(
                ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true",
                MachineProperties.PREFIX + ".instance-id=worker-a",
                MachineProperties.PREFIX + ".distributor.manual.machine-id=7",
                MachineProperties.PREFIX + ".guarder.enabled=false"
            )
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(MachineProperties.class)
                    .hasSingleBean(InstanceId.class)
                    .hasSingleBean(MachineStateStorage.class)
                    .hasSingleBean(ClockBackwardsSynchronizer.class)
                    .hasSingleBean(ManualMachineIdDistributor.class)
                    .hasSingleBean(MachineIdGuarder.class)
                    .hasSingleBean(GuardDistribute.class)
                    .hasSingleBean(MachineId.class)
                    .hasSingleBean(MachineIdHealthIndicator.class)
                    .hasSingleBean(CosIdMachineIdLifecycle.class);
                assertThat(context.getBean(InstanceId.class).getInstanceId()).isEqualTo("worker-a");
                assertThat(context.getBean(MachineId.class).getMachineId()).isEqualTo(7);
                assertThat(context.getBean(MachineIdGuarder.class)).isSameAs(MachineIdGuarder.NONE);
            });
    }

    @Test
    void backsOffForUserProvidedMachineIdAndDistributorInfrastructure() {
        MachineId machineId = new MachineId(11);
        MachineIdDistributor distributor = mock(MachineIdDistributor.class);
        MachineIdGuarder guarder = mock(MachineIdGuarder.class);

        this.contextRunner
            .withBean(MachineId.class, () -> machineId)
            .withBean(MachineIdDistributor.class, () -> distributor)
            .withBean(MachineIdGuarder.class, () -> guarder)
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .run(context -> {
                assertThat(context.getBean(MachineId.class)).isSameAs(machineId);
                assertThat(context.getBean(MachineIdDistributor.class)).isSameAs(distributor);
                assertThat(context.getBean(MachineIdGuarder.class)).isSameAs(guarder);
            });
    }

    @Test
    void doesNotCreateMachineBeansWhenCosIdIsDisabled() {
        this.contextRunner
            .withPropertyValues(
                "cosid.enabled=false",
                ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true"
            )
            .run(context -> assertThat(context)
                .doesNotHaveBean(MachineProperties.class)
                .doesNotHaveBean(MachineId.class)
                .doesNotHaveBean(GuardDistribute.class));
    }

    @Test
    void doesNotCreateMachineBeansWhenMachineIsDisabledOrMissing() {
        this.contextRunner
            .run(context -> assertThat(context)
                .doesNotHaveBean(MachineProperties.class)
                .doesNotHaveBean(MachineId.class)
                .doesNotHaveBean(GuardDistribute.class));
    }

    @Test
    void failsFastWhenManualDistributorHasNoMachineId() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .run(context -> assertThat(context.getStartupFailure())
                .hasRootCauseMessage("cosid.machine.distributor.manual can not be null."));
    }
}
