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
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.test.machine.lease.InMemoryMachineLeaseStore;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The per-instance locks of {@link LeaseStoreMachineIdDistributor}: dropped once no guard or revert holds or waits for
 * them, and still one lock per instance while any does.
 */
class LeaseStoreMachineIdDistributorLocksTest {
    private static final Duration SAFE_GUARD_DURATION = Duration.ofMinutes(1);

    @Test
    void locksAreDroppedOnceGuardAndRevertReturn() {
        String namespace = "locks_" + MockIdGenerator.INSTANCE.generateAsString();
        LeaseStoreMachineIdDistributor distributor = new LeaseStoreMachineIdDistributor(new InMemoryMachineLeaseStore(),
            new InMemoryMachineStateStorage(), ClockBackwardsSynchronizer.DEFAULT);

        for (int i = 0; i < 20; i++) {
            InstanceId instanceId = InstanceId.of("instance-" + i, false);
            distributor.distribute(namespace, 8, instanceId, SAFE_GUARD_DURATION);
            distributor.guard(namespace, instanceId, SAFE_GUARD_DURATION);
            distributor.revert(namespace, instanceId);
        }

        Assertions.assertEquals(0, distributor.lockCount());
    }

    @Test
    void concurrentGuardsOfOneInstanceNeverOverlap() throws Exception {
        String namespace = "locksOverlap_" + MockIdGenerator.INSTANCE.generateAsString();
        OverlapDetectingStore store = new OverlapDetectingStore();
        LeaseStoreMachineIdDistributor distributor = new LeaseStoreMachineIdDistributor(store, new InMemoryMachineStateStorage(),
            ClockBackwardsSynchronizer.DEFAULT);
        InstanceId owner = InstanceId.of("owner", false);
        distributor.distribute(namespace, 8, owner, SAFE_GUARD_DURATION);

        ExecutorService threads = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> guards = new ArrayList<>();
            for (int i = 0; i < 400; i++) {
                guards.add(threads.submit(() -> distributor.guard(namespace, owner, SAFE_GUARD_DURATION)));
            }
            for (Future<?> guard : guards) {
                guard.get(10, TimeUnit.SECONDS);
            }
        } finally {
            threads.shutdownNow();
        }

        Assertions.assertEquals(1, store.maxInFlight.get(), "renews of one instance ran concurrently");
        Assertions.assertEquals(0, distributor.lockCount());
    }

    /**
     * Counts the renews in flight at once.
     */
    private static final class OverlapDetectingStore extends InMemoryMachineLeaseStore {
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicInteger maxInFlight = new AtomicInteger();

        @Override
        public Optional<Lease> renew(Lease lease, long now) {
            int current = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(current, Math::max);
            try {
                Thread.yield();
                return super.renew(lease, now);
            } finally {
                inFlight.decrementAndGet();
            }
        }
    }
}
