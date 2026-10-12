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

import me.ahoo.cosid.internal.Checks;
import me.ahoo.cosid.machine.AbstractMachineIdDistributor;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdLease;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.NamespacedInstanceId;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * A {@link me.ahoo.cosid.machine.MachineIdDistributor} on top of a {@link MachineLeaseStore}, through
 * {@link MachineLeaseManager}.
 *
 * <p>It reuses everything {@link AbstractMachineIdDistributor} provides: local state, the clock-backwards watermark,
 * and the guarder and fail-fast lease on top. It keeps the current {@link Lease} of every instance it distributed, so
 * that guard and revert are compare-and-set on the version it last wrote.</p>
 *
 * @author ahoo wang
 * @since 3.4.0
 */
@Slf4j
public class LeaseStoreMachineIdDistributor extends AbstractMachineIdDistributor {
    private final MachineLeaseManager distributeLeaseManager;
    private final MachineLeaseManager leaseManager;
    private final ConcurrentHashMap<NamespacedInstanceId, Lease> leases = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<NamespacedInstanceId, Object> locks = new ConcurrentHashMap<>();
    private final LongSupplier localClock;
    
    public LeaseStoreMachineIdDistributor(MachineLeaseStore store, MachineStateStorage machineStateStorage,
                                          ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        this(new MachineLeaseManager(store), machineStateStorage, clockBackwardsSynchronizer);
    }
    
    public LeaseStoreMachineIdDistributor(MachineLeaseManager leaseManager, MachineStateStorage machineStateStorage,
                                          ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        this(leaseManager, leaseManager, machineStateStorage, clockBackwardsSynchronizer);
    }
    
    /**
     * Create a distributor whose distribution, usually once at startup, runs on its own lease manager, for example on
     * a store with a longer timeout: the first call also pays for connecting. Guard, revert and every re-claim after a
     * lost lease, which run under a lock, use {@code leaseManager}.
     */
    public LeaseStoreMachineIdDistributor(MachineLeaseManager distributeLeaseManager, MachineLeaseManager leaseManager,
                                          MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        this(distributeLeaseManager, leaseManager, machineStateStorage, clockBackwardsSynchronizer, System::currentTimeMillis);
    }
    
    LeaseStoreMachineIdDistributor(MachineLeaseManager distributeLeaseManager, MachineLeaseManager leaseManager,
                                   MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer,
                                   LongSupplier localClock) {
        super(machineStateStorage, clockBackwardsSynchronizer);
        this.distributeLeaseManager = distributeLeaseManager;
        this.leaseManager = leaseManager;
        this.localClock = localClock;
    }
    
    public MachineLeaseManager getDistributeLeaseManager() {
        return distributeLeaseManager;
    }
    
    public MachineLeaseManager getLeaseManager() {
        return leaseManager;
    }
    
    @Override
    protected MachineState distributeRemote(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
        Lease lease = distributeLeaseManager.acquire(namespace, instanceId, machineBit, safeGuardDuration);
        try {
            final long localBefore = localClock.getAsLong();
            checkClockSkew(namespace, instanceId, localBefore, distributeLeaseManager.getStore().serverTime(), safeGuardDuration);
        } catch (RuntimeException skewException) {
            try {
                distributeLeaseManager.release(lease, lease.getWatermark());
            } catch (RuntimeException releaseException) {
                skewException.addSuppressed(releaseException);
            }
            throw skewException;
        }
        leases.put(new NamespacedInstanceId(namespace, instanceId), lease);
        return lease.toMachineState();
    }
    
    @Override
    protected void guardRemote(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) {
        NamespacedInstanceId key = new NamespacedInstanceId(namespace, instanceId);
        synchronized (lockOf(key)) {
            Lease lease = currentLease(key, machineState).orElseThrow(() -> new MachineIdLostException(namespace, instanceId, machineState));
            Optional<Lease> renewed;
            final long localBefore = localClock.getAsLong();
            try {
                renewed = leaseManager.renew(lease);
            } catch (RuntimeException renewException) {
                // The write may still have landed (e.g. a timeout): the cached version could be stale. Forget it, so the
                // next guard takes the lease back from the store instead of failing on a version that is no longer current.
                leases.remove(key, lease);
                throw renewException;
            }
            if (renewed.isEmpty()) {
                leases.remove(key, lease);
                throw new MachineIdLostException(namespace, instanceId, machineState);
            }
            leases.put(key, renewed.get());
            // A renewal is to the store's time: comparing it with this host's clock gives the offset between them.
            checkClockSkew(namespace, instanceId, localBefore, renewed.get().getRenewedAt(), safeGuardDuration);
        }
    }
    
    /**
     * A host clock ahead of the store's makes this instance generate ids with timestamps in the store's future, which an
     * instance that takes the machine id over after the safe guard duration can generate again. Ahead by more than the
     * holder's margin {@code min(10s, safe-guard-duration / 10)}, the distribution or renewal fails, so the lease runs
     * out and the generators fail fast. A host behind the store is safe. A store without its own clock (ZooKeeper)
     * reports this host's time: no offset can be seen there.
     */
    private void checkClockSkew(String namespace, InstanceId instanceId, long localBefore, long storeTime, Duration safeGuardDuration) {
        if (MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION.equals(safeGuardDuration)) {
            return;
        }
        // The local time before the call: the store's time is read during it, so a slow call never looks like a host ahead.
        long hostAhead = localBefore - storeTime;
        long margin = Math.min(MachineIdLease.MAX_CLOCK_SKEW_MARGIN.toMillis(), safeGuardDuration.toMillis() / 10);
        if (hostAhead > margin) {
            String message = Checks.lenientFormat("The clock of this host is [%s] ms ahead of the machine id store's for [%s] @ namespace:[%s], "
                + "more than the margin of [%s] ms: an instance taking the machine id over could repeat its ids. Sync the host clock (NTP).",
                hostAhead, instanceId, namespace, margin);
            log.error(message);
            throw new IllegalStateException(message);
        }
        if (hostAhead > margin / 2 && log.isWarnEnabled()) {
            log.warn("The clock of this host is [{}] ms ahead of the machine id store's for [{}] @ namespace:[{}], over half the margin of [{}] ms.",
                hostAhead, instanceId, namespace, margin);
        }
    }
    
    @Override
    protected void revertRemote(String namespace, InstanceId instanceId, MachineState machineState) {
        NamespacedInstanceId key = new NamespacedInstanceId(namespace, instanceId);
        // Same lock as guard: a release between a renew and its cache update would fail on the version and keep the
        // machine id blocked until it expires.
        synchronized (lockOf(key)) {
            Optional<Lease> lease = currentLease(key, machineState);
            leases.remove(key);
            if (lease.isEmpty()) {
                if (log.isWarnEnabled()) {
                    log.warn("Revert [{}] @ namespace:[{}] - no lease held, the machine id may have been taken over.", instanceId, namespace);
                }
                return;
            }
            if (!leaseManager.release(lease.get(), machineState.getLastTimeStamp()) && log.isWarnEnabled()) {
                log.warn("Revert [{}] - taken over in the meantime, skip release.", lease.get());
            }
        }
    }
    
    private Object lockOf(NamespacedInstanceId key) {
        return locks.computeIfAbsent(key, absent -> new Object());
    }
    
    /**
     * The lease this distributor holds for the instance, or, after a restart, the one the store still has for it; only
     * if it is for {@code machineState}'s machine id.
     */
    private Optional<Lease> currentLease(NamespacedInstanceId key, MachineState machineState) {
        Lease lease = leases.computeIfAbsent(key, absent -> leaseManager.reclaim(absent.getNamespace(), absent.getInstanceId()).orElse(null));
        if (lease == null || lease.getMachineId() != machineState.getMachineId()) {
            return Optional.empty();
        }
        return Optional.of(lease);
    }
}
