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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.test.machine.lease.InMemoryMachineLeaseStore;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A host clock ahead of the store's: its ids carry timestamps in the store's future, and an instance that takes the
 * machine id over after the safe guard duration starts at the store's time, below those timestamps, and repeats ids.
 */
class HostClockAheadOfStoreTest {
    private static final Duration SAFE_GUARD = Duration.ofMinutes(5);
    private final AtomicLong storeClock = new AtomicLong(1_000_000_000L);
    private final AtomicLong hostAhead = new AtomicLong();
    private final InMemoryMachineLeaseStore store = new InMemoryMachineLeaseStore() {
        @Override
        public long serverTime() {
            return storeClock.get();
        }
    };
    
    private LeaseStoreMachineIdDistributor distributor() {
        MachineLeaseManager manager = new MachineLeaseManager(store);
        return new LeaseStoreMachineIdDistributor(manager, manager, MachineStateStorage.IN_MEMORY, ClockBackwardsSynchronizer.DEFAULT,
            () -> storeClock.get() + hostAhead.get());
    }
    
    @Test
    void aHostAheadByMoreThanTheMarginCannotTakeAMachineId() {
        hostAhead.set(30_000);
        InstanceId holder = InstanceId.of("holder", false);
        
        assertThrows(IllegalStateException.class, () -> distributor().distribute("ns", 1, holder, SAFE_GUARD));
        // The lease was given back.
        assertThat(store.claimReleased("ns", InstanceId.of("next", false), 1, store.serverTime()).isPresent(), equalTo(true));
    }
    
    @Test
    void aHostThatRunsAheadLaterFailsItsRenewal() {
        InstanceId holder = InstanceId.of("holder", false);
        LeaseStoreMachineIdDistributor distributor = distributor();
        distributor.distribute("ns", 1, holder, SAFE_GUARD);
        
        hostAhead.set(30_000);
        storeClock.addAndGet(60_000);
        assertThrows(IllegalStateException.class, () -> distributor.guard("ns", holder, SAFE_GUARD));
    }
    
    @Test
    void aSmallOffsetOrAHostBehindTheStoreIsFine() {
        InstanceId holder = InstanceId.of("holder", false);
        LeaseStoreMachineIdDistributor distributor = distributor();
        hostAhead.set(6_000);
        distributor.distribute("ns", 1, holder, SAFE_GUARD);
        distributor.guard("ns", holder, SAFE_GUARD);
        hostAhead.set(-3_600_000);
        distributor.guard("ns", holder, SAFE_GUARD);
    }
}
