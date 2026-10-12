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

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.HostAddressSupplier;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdGuarder;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;
import me.ahoo.cosid.test.machine.lease.InMemoryMachineLeaseStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class GuarderDisabledWarningTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdAutoConfiguration.class, CosIdMachineAutoConfiguration.class))
        .withBean(HostAddressSupplier.class, () -> () -> "10.0.0.10")
        .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true", MachineProperties.PREFIX + ".instance-id=worker-a",
            MachineProperties.PREFIX + ".guarder.enabled=false");
    
    @Test
    void warnsWhenALeaseStoreRunsWithoutTheGuarder(CapturedOutput output) {
        contextRunner
            .withBean(MachineIdDistributor.class,
                () -> new LeaseStoreMachineIdDistributor(new InMemoryMachineLeaseStore(), MachineStateStorage.IN_MEMORY, ClockBackwardsSynchronizer.DEFAULT))
            .run(context -> {
                assertThat(context.getBean(MachineIdGuarder.class)).isSameAs(MachineIdGuarder.NONE);
                assertThat(output).contains(CosIdMachineAutoConfiguration.GUARDER_DISABLED_WARNING);
            });
    }
    
    @Test
    void doesNotWarnForTheManualDistributor(CapturedOutput output) {
        contextRunner
            .withPropertyValues(MachineProperties.PREFIX + ".distributor.manual.machine-id=1")
            .run(context -> {
                assertThat(context.getBean(MachineIdGuarder.class)).isSameAs(MachineIdGuarder.NONE);
                assertThat(output).doesNotContain(CosIdMachineAutoConfiguration.GUARDER_DISABLED_WARNING);
            });
    }
}
