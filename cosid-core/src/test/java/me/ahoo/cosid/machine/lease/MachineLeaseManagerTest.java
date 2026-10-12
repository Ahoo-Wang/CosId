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

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.test.machine.lease.InMemoryMachineLeaseStore;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

class MachineLeaseManagerTest {
    
    /**
     * Without the guarder, an instance claims with the forever safe guard and never renews. That only protects it from
     * instances that do the same: an instance that runs the guarder judges the lease by its own safe guard duration and
     * takes it over, and both generate the same ids. This is why the guarder must be disabled on every instance of a
     * namespace, or on none.
     */
    @Test
    void aGuardedInstanceTakesOverTheMachineIdOfAnUnguardedOne() {
        AtomicLong clock = new AtomicLong(1_000_000);
        InMemoryMachineLeaseStore store = new InMemoryMachineLeaseStore() {
            @Override
            public long serverTime() {
                return clock.get();
            }
        };
        MachineLeaseManager manager = new MachineLeaseManager(store);
        Lease unguarded = manager.acquire("ns", InstanceId.of("unguarded", false), 1, MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION);
        Lease other = manager.acquire("ns", InstanceId.of("other", false), 1, Duration.ofMinutes(5));
        manager.release(other, clock.get());
        
        clock.addAndGet(Duration.ofMinutes(6).toMillis());
        manager.acquire("ns", InstanceId.of("released-taker", false), 1, Duration.ofMinutes(5));
        Lease guarded = manager.acquire("ns", InstanceId.of("guarded", false), 1, Duration.ofMinutes(5));
        
        assertThat(guarded.getMachineId(), equalTo(unguarded.getMachineId()));
        assertThat(manager.renew(unguarded).isPresent(), is(false));
    }
}
