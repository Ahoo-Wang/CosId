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
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;
import me.ahoo.cosid.test.machine.lease.InMemoryMachineLeaseStore;
import me.ahoo.cosid.util.ProcessId;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.atomic.AtomicReference;

class DefaultInstanceIdTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdAutoConfiguration.class, CosIdMachineAutoConfiguration.class))
        .withBean(HostAddressSupplier.class, () -> () -> "10.0.0.10")
        .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true", MachineProperties.PREFIX + ".guarder.enabled=false",
            MachineProperties.PREFIX + ".distributor.manual.machine-id=1");
    
    @Test
    void aNonStableDefaultInstanceIdIsUniquePerProcess() {
        AtomicReference<String> first = new AtomicReference<>();
        contextRunner.run(context -> {
            InstanceId instanceId = context.getBean(InstanceId.class);
            assertThat(instanceId.isStable()).isFalse();
            assertThat(instanceId.getInstanceId()).matches("10\\.0\\.0\\.10:" + ProcessId.CURRENT.getProcessId() + ":[0-9a-f]+");
            first.set(instanceId.getInstanceId());
        });
        contextRunner.run(context -> assertThat(context.getBean(InstanceId.class).getInstanceId()).isNotEqualTo(first.get()));
    }
    
    @Test
    void aStableDefaultInstanceIdKeepsHostAndProcessId() {
        contextRunner.withPropertyValues(MachineProperties.PREFIX + ".stable=true")
            .run(context -> assertThat(context.getBean(InstanceId.class))
                .isEqualTo(InstanceId.of("10.0.0.10", ProcessId.CURRENT.getProcessId(), true)));
    }
    
    @Test
    void anExplicitInstanceIdOrPortIsKept() {
        contextRunner.withPropertyValues(MachineProperties.PREFIX + ".instance-id=worker-a")
            .run(context -> assertThat(context.getBean(InstanceId.class).getInstanceId()).isEqualTo("worker-a"));
        contextRunner.withPropertyValues(MachineProperties.PREFIX + ".port=8080")
            .run(context -> assertThat(context.getBean(InstanceId.class).getInstanceId()).isEqualTo("10.0.0.10:8080"));
    }
    
    /**
     * Two processes with the same host and process id (containers on the host network, both PID 1) used to share the
     * default instance id, and the second took back the first one's live machine id.
     */
    @Test
    void twoProcessesOnTheSameHostAndProcessIdGetDifferentMachineIds() {
        InMemoryMachineLeaseStore store = new InMemoryMachineLeaseStore();
        ApplicationContextRunner shared = contextRunner
            .withBean(MachineIdDistributor.class,
                () -> new LeaseStoreMachineIdDistributor(store, MachineStateStorage.IN_MEMORY, ClockBackwardsSynchronizer.DEFAULT));
        shared.run(first -> shared.run(second -> assertThat(second.getBean(MachineId.class).getMachineId())
            .isNotEqualTo(first.getBean(MachineId.class).getMachineId())));
    }
    
    @Test
    void aStableInstanceWithAnExplicitIdTakesBackItsMachineId() {
        InMemoryMachineLeaseStore store = new InMemoryMachineLeaseStore();
        ApplicationContextRunner stable = contextRunner
            .withPropertyValues(MachineProperties.PREFIX + ".stable=true", MachineProperties.PREFIX + ".instance-id=worker-a")
            .withBean(MachineIdDistributor.class,
                () -> new LeaseStoreMachineIdDistributor(store, MachineStateStorage.IN_MEMORY, ClockBackwardsSynchronizer.DEFAULT));
        AtomicReference<Integer> machineId = new AtomicReference<>();
        stable.run(context -> machineId.set(context.getBean(MachineId.class).getMachineId()));
        stable.run(context -> assertThat(context.getBean(MachineId.class).getMachineId()).isEqualTo(machineId.get()));
    }
}
