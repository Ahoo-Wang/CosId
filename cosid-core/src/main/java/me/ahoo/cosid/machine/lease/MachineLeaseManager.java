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
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdOverflowException;

import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Optional;

/**
 * The single machine id allocation algorithm, on top of any {@link MachineLeaseStore}.
 *
 * <p>Order: take back this instance's own lease, then a released machine id, then a never-used one, and only then
 * an expired one. A stable instance never takes over another instance's expired lease, and with
 * {@link MachineIdDistributor#FOREVER_SAFE_GUARD_DURATION} no lease ever expires.</p>
 *
 * <p>Every time comes from {@link MachineLeaseStore#serverTime()}, except the last timestamp written on
 * {@link #release release}, which is the generator's watermark from this host's clock. Hosts' clocks must therefore be
 * kept in sync: a host running ahead only delays the next owner of a released machine id (it waits until its clock
 * passes the watermark), which is safe but slow.</p>
 *
 * @author ahoo wang
 * @since 3.4.0
 */
@Slf4j
public class MachineLeaseManager {
    /**
     * Rounds of the claim sequence: a claim that lost a race to a concurrent instance is tried again before reporting
     * an overflow.
     */
    static final int CLAIM_ROUNDS = 3;
    private final MachineLeaseStore store;
    
    public MachineLeaseManager(MachineLeaseStore store) {
        this.store = Preconditions.checkNotNull(store, "store can not be null!");
    }
    
    public MachineLeaseStore getStore() {
        return store;
    }
    
    /**
     * Take a machine id for the instance.
     *
     * @throws MachineIdOverflowException if every machine id is held by a live (or stable) owner
     */
    public Lease acquire(String namespace, InstanceId instanceId, int machineBit, Duration safeGuardDuration) throws MachineIdOverflowException {
        int maxMachineId = MachineIdDistributor.maxMachineId(machineBit);
        Optional<Lease> lease = Optional.empty();
        for (int round = 0; round < CLAIM_ROUNDS && lease.isEmpty(); round++) {
            lease = claimOnce(namespace, instanceId, maxMachineId, safeGuardDuration);
        }
        if (lease.isEmpty()) {
            throw new MachineIdOverflowException(MachineIdDistributor.totalMachineIds(machineBit), instanceId);
        }
        if (log.isInfoEnabled()) {
            log.info("Acquire [{}].", lease.get());
        }
        return lease.get();
    }
    
    private Optional<Lease> claimOnce(String namespace, InstanceId instanceId, int maxMachineId, Duration safeGuardDuration) {
        long now = store.serverTime();
        Optional<Lease> lease = store.claimSelf(namespace, instanceId, now);
        if (lease.isEmpty()) {
            lease = store.claimReleased(namespace, instanceId, maxMachineId, now);
        }
        if (lease.isEmpty()) {
            lease = store.claimNew(namespace, instanceId, maxMachineId, now);
        }
        if (lease.isEmpty() && canTakeOverExpired(instanceId, safeGuardDuration)) {
            lease = store.claimExpired(namespace, instanceId, maxMachineId, now - safeGuardDuration.toMillis(), now);
        }
        return lease;
    }
    
    /**
     * A stable instance must not recycle others' machine ids; with the forever duration nothing ever expires.
     */
    private static boolean canTakeOverExpired(InstanceId instanceId, Duration safeGuardDuration) {
        return !instanceId.isStable() && !MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION.equals(safeGuardDuration);
    }
    
    /**
     * Take back the lease this instance holds, for instance after a restart without a lease in memory.
     *
     * @return empty if the instance holds none: it was released or taken over
     */
    public Optional<Lease> reclaim(String namespace, InstanceId instanceId) {
        return store.claimSelf(namespace, instanceId, store.serverTime());
    }
    
    /**
     * Renew a lease.
     *
     * @return the renewed lease, or empty if it was taken over in the meantime
     */
    public Optional<Lease> renew(Lease lease) {
        return store.renew(lease, store.serverTime());
    }
    
    /**
     * Release a lease on shutdown.
     *
     * @param lastTimestamp the generator's last timestamp (host clock), the next owner's clock-backwards watermark
     * @return {@code false} if the lease was taken over in the meantime
     */
    public boolean release(Lease lease, long lastTimestamp) {
        return store.release(lease, lastTimestamp);
    }
}
