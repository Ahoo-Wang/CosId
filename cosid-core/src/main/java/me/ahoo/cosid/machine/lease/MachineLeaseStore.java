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
 * <h2>Time contract</h2>
 *
 * <p>All timestamps are milliseconds, and every {@code now} and {@code expiredBefore} passed to a store must come from
 * that same store's {@link #serverTime()}. The {@link Lease#getLastTimestamp() last timestamp} is both the liveness
 * check and the clock-backwards watermark of the next owner: if instance A wrote it with its own clock and instance B
 * computed {@code expiredBefore} from a faster clock, B would recycle A's lease early and both would generate the same
 * ids. Stores with a server clock (Redis, databases, MongoDB) override {@link #serverTime()}; stores without one
 * (ZooKeeper) fall back to the host clock, so their hosts' clocks must be kept in sync.</p>
 *
 * <p>Every write sets the last timestamp to {@code max(stored + 1, now)} (or {@code now} for a new lease): it never moves
 * backwards and strictly increases, which also makes it the {@link Lease#getVersion() version} that renew and release
 * compare against.</p>
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
     * @param maxMachineId the largest machine id allowed: released ids above it (left over from a larger machine bit)
     *                     are never returned
     * @return the new lease, or empty if no released machine id up to {@code maxMachineId} is left
     */
    Optional<Lease> claimReleased(String namespace, InstanceId instanceId, int maxMachineId, long now);
    
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
     * @param maxMachineId  the largest machine id allowed: expired ids above it are never returned
     * @param expiredBefore leases with a last timestamp at or before this time are expired
     * @return the new lease, or empty if no lease up to {@code maxMachineId} has expired
     */
    Optional<Lease> claimExpired(String namespace, InstanceId instanceId, int maxMachineId, long expiredBefore, long now);
    
    /**
     * Renew a lease to {@code now} if it is still the current {@link Lease#getVersion() version}.
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
     * The store's clock, in milliseconds, when it has one; otherwise this host's clock. The source of every
     * {@code now} and {@code expiredBefore} passed to this store.
     */
    default long serverTime() {
        return System.currentTimeMillis();
    }
}
