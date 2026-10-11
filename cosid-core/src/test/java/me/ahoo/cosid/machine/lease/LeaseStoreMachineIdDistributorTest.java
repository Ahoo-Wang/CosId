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

package me.ahoo.cosid.machine.lease;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InMemoryMachineStateStorage;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineIdOverflowException;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.test.machine.distributor.MachineIdDistributorSpec;
import me.ahoo.cosid.test.machine.lease.InMemoryMachineLeaseStore;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

/**
 * Runs {@link MachineIdDistributorSpec} through {@link LeaseStoreMachineIdDistributor} on the in-memory store.
 */
class LeaseStoreMachineIdDistributorTest extends MachineIdDistributorSpec {
    private final InMemoryMachineLeaseStore store = new InMemoryMachineLeaseStore();
    
    @Override
    protected MachineIdDistributor getDistributor() {
        return new LeaseStoreMachineIdDistributor(store, MachineStateStorage.IN_MEMORY, ClockBackwardsSynchronizer.DEFAULT);
    }
    
    @Test
    void guardFailsOnceTheLeaseWasTakenOver() {
        String namespace = "takenOver_" + MockIdGenerator.INSTANCE.generateAsString();
        InstanceId owner = InstanceId.of("owner", false);
        LeaseStoreMachineIdDistributor distributor = new LeaseStoreMachineIdDistributor(store, new InMemoryMachineStateStorage(),
            ClockBackwardsSynchronizer.DEFAULT);
        distributor.distribute(namespace, 1, owner, Duration.ofMinutes(1));
        // Another instance takes the machine id over behind the owner's back.
        store.claimExpired(namespace, InstanceId.of("thief", false), 1, Long.MAX_VALUE - 1, store.serverTime()).orElseThrow();
        
        Assertions.assertThrows(MachineIdLostException.class, () -> distributor.guard(namespace, owner, Duration.ofMinutes(1)));
    }
    
    @Test
    void restartedDistributorGuardsAndRevertsThroughReclaim() {
        String namespace = "restart_" + MockIdGenerator.INSTANCE.generateAsString();
        InstanceId owner = InstanceId.of("owner", false);
        InMemoryMachineStateStorage storage = new InMemoryMachineStateStorage();
        int machineId = new LeaseStoreMachineIdDistributor(store, storage, ClockBackwardsSynchronizer.DEFAULT)
            .distribute(namespace, 4, owner, Duration.ofMinutes(1)).getMachineId();
        // A new distributor (no lease in memory), same local storage and store.
        LeaseStoreMachineIdDistributor restarted = new LeaseStoreMachineIdDistributor(store, storage, ClockBackwardsSynchronizer.DEFAULT);
        
        Assertions.assertDoesNotThrow(() -> restarted.guard(namespace, owner, Duration.ofMinutes(1)));
        restarted.revert(namespace, owner);
        
        Lease next = store.claimReleased(namespace, InstanceId.of("next", false), 15, store.serverTime()).orElseThrow();
        Assertions.assertEquals(machineId, next.getMachineId());
    }
    
    @Test
    void stableInstanceDoesNotTakeOverExpiredLeases() {
        String namespace = "stableClaimer_" + MockIdGenerator.INSTANCE.generateAsString();
        store.claimNew(namespace, InstanceId.of("expired0", false), 1, 1).orElseThrow();
        store.claimNew(namespace, InstanceId.of("expired1", false), 1, 1).orElseThrow();
        LeaseStoreMachineIdDistributor distributor = new LeaseStoreMachineIdDistributor(store, new InMemoryMachineStateStorage(),
            ClockBackwardsSynchronizer.DEFAULT);
        
        Assertions.assertThrows(MachineIdOverflowException.class,
            () -> distributor.distribute(namespace, 1, InstanceId.of("stable", true), Duration.ofMillis(1)).getMachineId());
        Assertions.assertDoesNotThrow(() -> distributor.distribute(namespace, 1, InstanceId.of("temporary", false), Duration.ofMillis(1)));
    }
}
