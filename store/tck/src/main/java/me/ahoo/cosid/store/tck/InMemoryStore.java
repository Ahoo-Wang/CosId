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
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.time.TimeSource;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The in-memory reference store (06-stores.md §3): passes every contract, for the unit tests of core and Spring.
 * Instances are independent; {@link #ranges()} and {@link #slots()} return this store.
 */
public final class InMemoryStore implements Store, RangeStore, SlotLeaseStore {
    private final TimeSource storeClock;

    /**
     * A store on the system clock.
     */
    public InMemoryStore() {
        this(TimeSource.SYSTEM);
    }

    /**
     * A store on its own clock, for tests that move the store clock apart from the client clock.
     *
     * @param storeClock the store clock
     */
    public InMemoryStore(TimeSource storeClock) {
        this.storeClock = Objects.requireNonNull(storeClock, "storeClock");
    }

    @Override
    public RangeStore ranges() {
        return this;
    }

    @Override
    public SlotLeaseStore slots() {
        return this;
    }

    @Override
    public Range reserve(String key, long size, long initialOffset) {
        throw new UnsupportedOperationException("R1-C");
    }

    @Override
    public boolean seal(String key) {
        throw new UnsupportedOperationException("R1-C");
    }

    @Override
    public AcquireResult acquire(String namespace, Owner owner, int limit, Duration ttl, Duration tolerance, OptionalInt preferredSlot) {
        throw new UnsupportedOperationException("R1-C");
    }

    @Override
    public Optional<SlotLease> renew(SlotLease lease, Duration ttl, Duration tolerance) {
        throw new UnsupportedOperationException("R1-C");
    }

    @Override
    public boolean release(SlotLease lease, long lastIssued) {
        throw new UnsupportedOperationException("R1-C");
    }

    @Override
    public void close() {
    }
}
