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

import me.ahoo.cosid.machine.InstanceId;

import java.util.Optional;

/**
 * Storage primitives for machine id leases, with compare-and-set semantics.
 *
 * <p>Backends implement only these primitives; the allocation order (self, released, new, expired), guarding and
 * clock-backwards handling live in core. Every claim is atomic: when several instances claim concurrently, each
 * machine id goes to at most one of them. {@code MachineLeaseStoreSpec} in {@code cosid-test} is the compatibility kit
 * every implementation must pass.</p>
 *
 * <p>All timestamps are milliseconds. Claims write {@code max(stored lastTimestamp, now)} as the new
 * {@link Lease#getLastTimestamp() last timestamp}, so it never moves backwards.</p>
 *
 * @author ahoo wang
 * @since 3.4.0
 */
public interface MachineLeaseStore {
    
    /**
     * Take back the lease this instance already holds, renewing it to {@code now}, whether or not it has expired.
     *
     * @return the renewed lease, or empty if the instance holds none (it was released or recycled)
     */
    Optional<Lease> claimSelf(String namespace, InstanceId instanceId, long now);
    
    /**
     * Take a machine id that a non-stable instance released on shutdown.
     *
     * @return the new lease, or empty if no released machine id is left
     */
    Optional<Lease> claimReleased(String namespace, InstanceId instanceId, long now);
    
    /**
     * Take the next machine id that was never used in the namespace.
     *
     * @param maxMachineId the largest machine id allowed
     * @return the new lease, or empty if every machine id up to {@code maxMachineId} is in use
     */
    Optional<Lease> claimNew(String namespace, InstanceId instanceId, int maxMachineId, long now);
    
    /**
     * Take over a machine id whose owner has not renewed it since {@code expiredBefore}. Leases of stable instances are
     * never taken over.
     *
     * @param expiredBefore leases with a last timestamp at or before this time are expired
     * @return the new lease, or empty if no lease has expired
     */
    Optional<Lease> claimExpired(String namespace, InstanceId instanceId, long expiredBefore, long now);
    
    /**
     * Renew a lease to {@code now} if it is still the current version.
     *
     * @return the renewed lease, or empty if the lease was taken over, released or changed since
     */
    Optional<Lease> renew(Lease lease, long now);
    
    /**
     * Release a lease on shutdown, if it is still the current version. A stable instance keeps its machine id (only the
     * last timestamp is updated); otherwise the machine id becomes available to {@link #claimReleased}.
     *
     * @param lastTimestamp the last timestamp to keep for the next owner, so that it waits if the clock moved back
     * @return {@code false} if the lease was taken over or changed since
     */
    boolean release(Lease lease, long lastTimestamp);
    
    /**
     * The store's clock, in milliseconds, when it has one; otherwise this host's clock.
     */
    default long serverTime() {
        return System.currentTimeMillis();
    }
}
