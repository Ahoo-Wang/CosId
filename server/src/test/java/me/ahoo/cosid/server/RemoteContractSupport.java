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

package me.ahoo.cosid.server;

import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.store.remote.RemoteStore;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The primitives of a fresh {@link RemoteStore} for each {@code newStore()} of the contracts, closing its HTTP client
 * when the contract closes the primitive.
 */
final class RemoteContractSupport {
    private RemoteContractSupport() {
    }

    static RangeStore ranges(TestServer server) {
        RemoteStore remote = server.client();
        RangeStore ranges = remote.ranges();
        return new RangeStore() {
            @Override
            public Range reserve(String key, long size, long initialOffset) {
                return ranges.reserve(key, size, initialOffset);
            }

            @Override
            public boolean seal(String key) {
                return ranges.seal(key);
            }

            @Override
            public void close() {
                remote.close();
            }
        };
    }

    static SlotLeaseStore slots(TestServer server) {
        RemoteStore remote = server.client();
        SlotLeaseStore slots = remote.slots();
        return new SlotLeaseStore() {
            @Override
            public AcquireResult acquire(String namespace, Owner owner, int limit, Duration ttl, Duration tolerance, int slotOffset, OptionalInt preferredSlot) {
                return slots.acquire(namespace, owner, limit, ttl, tolerance, slotOffset, preferredSlot);
            }

            @Override
            public Optional<SlotLease> renew(SlotLease lease, Duration ttl, Duration tolerance) {
                return slots.renew(lease, ttl, tolerance);
            }

            @Override
            public boolean release(SlotLease lease, long lastIssued) {
                return slots.release(lease, lastIssued);
            }

            @Override
            public void close() {
                remote.close();
            }
        };
    }
}
