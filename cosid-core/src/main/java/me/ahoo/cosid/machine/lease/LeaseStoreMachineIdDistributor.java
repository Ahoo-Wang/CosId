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

import me.ahoo.cosid.machine.AbstractMachineIdDistributor;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.NamespacedInstanceId;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
    private final MachineLeaseManager leaseManager;
    private final ConcurrentHashMap<NamespacedInstanceId, Lease> leases = new ConcurrentHashMap<>();
    
    public LeaseStoreMachineIdDistributor(MachineLeaseStore store, MachineStateStorage machineStateStorage,
                                          ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        this(new MachineLeaseManager(store), machineStateStorage, clockBackwardsSynchronizer);
    }
    
    public LeaseStoreMachineIdDistributor(MachineLeaseManager leaseManager, MachineStateStorage machineStateStorage,
                                          ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        super(machineStateStorage, clockBackwardsSynchronizer);
        this.leaseManager = leaseManager;
    }
    
    public MachineLeaseManager getLeaseManager() {
        return leaseManager;
    }
    
    @Override
    protected MachineState distributeRemote(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
        Lease lease = leaseManager.acquire(namespace, instanceId, machineBit, safeGuardDuration);
        leases.put(new NamespacedInstanceId(namespace, instanceId), lease);
        return lease.toMachineState();
    }
    
    @Override
    protected void guardRemote(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) {
        NamespacedInstanceId key = new NamespacedInstanceId(namespace, instanceId);
        Lease lease = currentLease(key, machineState).orElseThrow(() -> new MachineIdLostException(namespace, instanceId, machineState));
        Optional<Lease> renewed = leaseManager.renew(lease);
        if (renewed.isEmpty()) {
            leases.remove(key, lease);
            throw new MachineIdLostException(namespace, instanceId, machineState);
        }
        leases.put(key, renewed.get());
    }
    
    @Override
    protected void revertRemote(String namespace, InstanceId instanceId, MachineState machineState) {
        NamespacedInstanceId key = new NamespacedInstanceId(namespace, instanceId);
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
