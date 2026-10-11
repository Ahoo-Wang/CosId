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

package me.ahoo.cosid.machine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class GuardDistributeTest {

    @Test
    void distribute() {
        InMemoryMachineStateStorage machineStateStorage = new InMemoryMachineStateStorage();
        ManualMachineIdDistributor distributor = new ManualMachineIdDistributor(1, machineStateStorage, ClockBackwardsSynchronizer.DEFAULT);
        GuardDistribute guardDistribute = new GuardDistribute(distributor, MachineIdGuarder.NONE);
        String namespace = "test";
        InstanceId instanceId = InstanceId.of("test", true);
        MachineState machineState = guardDistribute.distribute(namespace, 10, instanceId, Duration.ofMinutes(1));
        assertThat(machineState.getMachineId(), equalTo(1));
        assertThat(machineStateStorage.get(namespace, instanceId), equalTo(machineState));
        assertThat(guardDistribute.getDistributed(), contains(new NamespacedInstanceId(namespace, instanceId)));
    }

    @Test
    void leaseStartsWhenDistributeStarts() {
        long distributeMillis = 300;
        Duration safeGuardDuration = Duration.ofSeconds(1);
        MachineIdDistributor slowDistributor = new ManualMachineIdDistributor(1, new InMemoryMachineStateStorage(), ClockBackwardsSynchronizer.DEFAULT) {
            @Override
            protected MachineState distributeRemote(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
                try {
                    Thread.sleep(distributeMillis);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
                return super.distributeRemote(namespace, machineBit, instanceId, safeGuardDuration);
            }
        };
        DefaultMachineIdGuarder guarder = new DefaultMachineIdGuarder(slowDistributor, safeGuardDuration);
        GuardDistribute guardDistribute = new GuardDistribute(slowDistributor, guarder);
        InstanceId instanceId = InstanceId.of("slow", false);
        long before = System.currentTimeMillis();

        guardDistribute.distribute("test", 10, instanceId, safeGuardDuration);

        long validUntil = guardDistribute.getLease("test", instanceId).getValidUntil();
        long validityMillis = MachineIdLease.validityMillis(safeGuardDuration);
        assertThat(validUntil, greaterThanOrEqualTo(before + validityMillis));
        // Counted from the end of the distribute call it would be at least before + distributeMillis + validityMillis.
        assertThat(validUntil, lessThan(before + distributeMillis + validityMillis));
    }
}
