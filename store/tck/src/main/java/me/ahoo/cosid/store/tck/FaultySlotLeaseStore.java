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

package me.ahoo.cosid.store.tck;

import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.store.tck.FaultInjector.Operation;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A {@link SlotLeaseStore} that applies the faults of a {@link FaultInjector} before delegating, for the lease tests
 * of R1 (delays, timeouts, a lost renewal response then its replay).
 */
public final class FaultySlotLeaseStore implements SlotLeaseStore {
    private final SlotLeaseStore delegate;
    private final FaultInjector faults;

    public FaultySlotLeaseStore(SlotLeaseStore delegate, FaultInjector faults) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.faults = Objects.requireNonNull(faults, "faults");
    }

    @Override
    public AcquireResult acquire(String namespace, Owner owner, int limit, Duration ttl, Duration tolerance, OptionalInt preferredSlot) {
        return faults.apply(Operation.ACQUIRE, () -> delegate.acquire(namespace, owner, limit, ttl, tolerance, preferredSlot));
    }

    @Override
    public Optional<SlotLease> renew(SlotLease lease, Duration ttl, Duration tolerance) {
        return faults.apply(Operation.RENEW, () -> delegate.renew(lease, ttl, tolerance));
    }

    @Override
    public boolean release(SlotLease lease, long lastIssued) {
        return faults.apply(Operation.RELEASE, () -> delegate.release(lease, lastIssued));
    }

    @Override
    public void close() {
        delegate.close();
    }
}
