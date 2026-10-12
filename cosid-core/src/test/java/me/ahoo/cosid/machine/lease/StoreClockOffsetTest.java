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
import me.ahoo.cosid.machine.MachineIdLease;
import me.ahoo.cosid.test.machine.lease.InMemoryMachineLeaseStore;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * On a store with its own clock (Redis TIME, a database clock, MongoDB, the proxy), an offset between the holder's host
 * clock and the store's does not matter: the holder counts its validity from its own clock and the store counts expiry
 * from its own, so the offset cancels. The holder stops before anyone can take the machine id over. ZooKeeper has no
 * clock of its own: there, the hosts' clocks must agree within the margin.
 */
class StoreClockOffsetTest {
    private static final Duration SAFE_GUARD = Duration.ofMinutes(5);
    
    @ParameterizedTest
    @ValueSource(longs = {-3_600_000, -30_000, 0, 30_000, 3_600_000})
    void theHolderStopsBeforeTheMachineIdCanBeTakenOver(long hostAheadOfStoreMillis) {
        AtomicLong storeClock = new AtomicLong(1_000_000_000);
        InMemoryMachineLeaseStore store = new InMemoryMachineLeaseStore() {
            @Override
            public long serverTime() {
                return storeClock.get();
            }
        };
        MachineLeaseManager manager = new MachineLeaseManager(store);
        Lease held = manager.acquire("ns", InstanceId.of("holder", false), 1, SAFE_GUARD);
        Lease other = manager.acquire("ns", InstanceId.of("other", false), 1, SAFE_GUARD);
        MachineIdLease lease = new MachineIdLease("ns", InstanceId.of("holder", false), SAFE_GUARD);
        lease.renew(storeClock.get() + hostAheadOfStoreMillis);
        
        for (long elapsed = 0; elapsed <= SAFE_GUARD.toMillis() + 1_000; elapsed += 500) {
            storeClock.set(1_000_000_000 + elapsed);
            long hostNow = storeClock.get() + hostAheadOfStoreMillis;
            boolean takeable = store.serverTime() - held.getRenewedAt() > SAFE_GUARD.toMillis();
            if (takeable) {
                assertThat("holder still generating at " + elapsed + " ms", lease.isValid(hostNow), is(false));
            }
        }
        // The other machine id is still renewed: the taker can only take the holder's.
        manager.renew(other).orElseThrow();
        assertThat(manager.acquire("ns", InstanceId.of("taker", false), 1, SAFE_GUARD).getMachineId(), equalTo(held.getMachineId()));
    }
}
