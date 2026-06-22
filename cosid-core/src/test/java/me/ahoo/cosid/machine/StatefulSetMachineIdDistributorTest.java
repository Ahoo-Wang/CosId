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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.machine.k8s.StatefulSetMachineIdDistributor;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ClearEnvironmentVariable;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

import java.time.Duration;

class StatefulSetMachineIdDistributorTest {

    @Test
    @SetEnvironmentVariable(key = StatefulSetMachineIdDistributor.HOSTNAME_KEY, value = "cosid-v1-host-12")
    void resolveMachineIdShouldUseNumericSuffixAfterLastDash() {
        assertEquals(12, StatefulSetMachineIdDistributor.resolveMachineId());
    }

    @Test
    @ClearEnvironmentVariable(key = StatefulSetMachineIdDistributor.HOSTNAME_KEY)
    void resolveMachineIdShouldRejectMissingHostnameWithMessage() {
        NullPointerException exception = assertThrows(NullPointerException.class,
            StatefulSetMachineIdDistributor::resolveMachineId);

        assertEquals("HOSTNAME can not be null.", exception.getMessage());
    }

    @Test
    @SetEnvironmentVariable(key = StatefulSetMachineIdDistributor.HOSTNAME_KEY, value = "cosid")
    void resolveMachineIdShouldRejectHostnameWithoutOrdinalDelimiter() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            StatefulSetMachineIdDistributor::resolveMachineId);

        assertEquals("The format of hostName:[cosid] is incorrect.", exception.getMessage());
    }

    @Test
    @SetEnvironmentVariable(key = StatefulSetMachineIdDistributor.HOSTNAME_KEY, value = "cosid-host-blue")
    void resolveMachineIdShouldPropagateInvalidNumericSuffix() {
        NumberFormatException exception = assertThrows(NumberFormatException.class,
            StatefulSetMachineIdDistributor::resolveMachineId);

        assertTrue(exception.getMessage().contains("blue"));
    }

    @Test
    @SetEnvironmentVariable(key = StatefulSetMachineIdDistributor.HOSTNAME_KEY, value = "cosid-host-3")
    void distributeShouldUseResolvedOrdinalAndPersistLocalState() {
        InMemoryMachineStateStorage storage = new InMemoryMachineStateStorage();
        StatefulSetMachineIdDistributor distributor = new StatefulSetMachineIdDistributor(storage, ClockBackwardsSynchronizer.DEFAULT);
        InstanceId instanceId = InstanceId.of("pod", true);

        MachineState state = distributor.distribute("k8s", 2, instanceId, Duration.ofMinutes(1));

        assertEquals(3, state.getMachineId());
        assertEquals(3, storage.get("k8s", instanceId).getMachineId());
    }

    @Test
    @SetEnvironmentVariable(key = StatefulSetMachineIdDistributor.HOSTNAME_KEY, value = "cosid-host-4")
    void distributeShouldRejectResolvedOrdinalOutsideMachineBitRange() {
        StatefulSetMachineIdDistributor distributor = new StatefulSetMachineIdDistributor(
            new InMemoryMachineStateStorage(), ClockBackwardsSynchronizer.DEFAULT);
        InstanceId instanceId = InstanceId.of("pod", true);

        MachineIdOverflowException exception = assertThrows(MachineIdOverflowException.class,
            () -> distributor.distribute("k8s", 2, instanceId, Duration.ofMinutes(1)));

        assertEquals(4, exception.getTotalMachineIds());
        assertSame(instanceId, exception.getInstanceId());
        assertTrue(exception.getMessage().contains("totalMachineIds:[4]"));
    }

    @Test
    @SetEnvironmentVariable(key = StatefulSetMachineIdDistributor.HOSTNAME_KEY, value = "cosid-host-0")
    void revertAndGuardShouldUseLocalStateWithoutRemoteSideEffects() {
        InMemoryMachineStateStorage storage = new InMemoryMachineStateStorage();
        StatefulSetMachineIdDistributor distributor = new StatefulSetMachineIdDistributor(storage, ClockBackwardsSynchronizer.DEFAULT);
        InstanceId instanceId = InstanceId.of("pod", true);
        distributor.distribute("k8s", 1, instanceId, Duration.ofMinutes(1));

        distributor.guard("k8s", instanceId, Duration.ofMinutes(1));
        distributor.revert("k8s", instanceId);

        assertSame(MachineState.NOT_FOUND, storage.get("k8s", instanceId));
    }
}
