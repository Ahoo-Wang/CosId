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

package me.ahoo.cosid.lease;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Leases time windows on machine slots, the lease primitive of a store (05-machine-lease.md §2). Every state change is
 * an atomic conditional update of one slot; the store clock stamps it.
 */
public interface SlotLeaseStore extends AutoCloseable {
    /**
     * Acquires a slot, trying in order (§2.1): one this owner already holds, {@code preferredSlot}, the slot this owner
     * key held last, the lowest free slot.
     *
     * @param namespace     the namespace
     * @param owner         the owner
     * @param limit         the slots are {@code [0, limit)}
     * @param ttl           the lease expires this long after the write
     * @param tolerance     the ceiling lies this far beyond the expiry (D20)
     * @param slotOffset    the offset the clients add to the slot to get the node of their ids (api-freeze.md §2.1.1); it
     *                      is recorded with the limit and the tolerance and does not change which slot is chosen
     * @param preferredSlot the slot to try before the others
     * @return the lease, or {@link AcquireResult.Exhausted}
     * @throws me.ahoo.cosid.NamespaceMismatchException if the namespace was first used with another limit, tolerance or
     *                                                  slot offset (metadata written before the slot offset existed
     *                                                  reads as offset 0)
     */
    AcquireResult acquire(String namespace, Owner owner, int limit, Duration ttl, Duration tolerance, int slotOffset, OptionalInt preferredSlot);

    /**
     * Renews a lease; idempotent, the fence stays.
     *
     * @param lease     the lease, as last returned
     * @param ttl       the lease expires this long after the write, never earlier than before
     * @param tolerance the ceiling lies this far beyond the expiry
     * @return the renewed lease, or empty if the owner or fence no longer match
     */
    Optional<SlotLease> renew(SlotLease lease, Duration ttl, Duration tolerance);

    /**
     * Releases a lease; the next holder's floor becomes {@code max(floor, min(lastIssued, ceiling))}.
     *
     * @param lease      the lease, as last returned
     * @param lastIssued the largest timestamp issued under the lease, in milliseconds
     * @return whether the lease was released; false if the owner or fence no longer match
     */
    boolean release(SlotLease lease, long lastIssued);

    @Override
    default void close() {
    }
}
