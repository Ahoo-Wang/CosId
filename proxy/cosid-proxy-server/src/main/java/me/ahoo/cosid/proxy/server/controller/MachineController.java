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

package me.ahoo.cosid.proxy.server.controller;

import me.ahoo.cosid.internal.Checks;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineIdOverflowException;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.lease.Lease;
import me.ahoo.cosid.machine.lease.MachineLeaseManager;
import me.ahoo.cosid.machine.lease.MachineLeaseStore;
import me.ahoo.cosid.proxy.api.MachineApi;
import me.ahoo.cosid.proxy.server.configuration.ProxyServerProperties;

import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Machine resource controller, version 1: kept for 3.x clients and deprecated, use {@link MachineLeaseController}.
 *
 * <p>Each call runs the lease algorithm on the same backend {@link MachineLeaseStore} as version 2, so the server keeps
 * no state: distribute acquires a lease, guard takes the instance's own lease back (renewing it), revert releases it.
 * Clients that send their {@code machineId} have it checked against the lease; clients that do not still work.</p>
 *
 * @author ahoo wang
 * @deprecated since 4.0, use the version 2 lease API ({@link me.ahoo.cosid.proxy.api.MachineLeaseApi}).
 */
@Deprecated
@Slf4j
@RestController
public class MachineController implements MachineApi {
    private static final int MAX_MACHINE_BIT = Integer.SIZE - 1;
    private final MachineLeaseManager leaseManager;
    private final Duration maxClockSkew;
    
    public MachineController(MachineLeaseStore store) {
        this(store, new ProxyServerProperties());
    }
    
    @Autowired
    public MachineController(MachineLeaseStore store, ProxyServerProperties proxyServerProperties) {
        this.leaseManager = new MachineLeaseManager(store);
        this.maxClockSkew = proxyServerProperties.getMaxClockSkew();
    }
    
    /**
     * Distribute a machine ID, the operation is idempotent.
     */
    @Override
    @Operation(summary = "Distribute a machine ID, the operation is idempotent.", deprecated = true)
    public MachineState distribute(@PathVariable String namespace, int machineBit, String instanceId, boolean stable, String safeGuardDuration) throws MachineIdOverflowException {
        checkNamespaceAndInstanceId(namespace, instanceId);
        Checks.checkArgument(machineBit > 0 && machineBit <= MAX_MACHINE_BIT, "machineBit:[%s] must be in [1, %s]!", machineBit, MAX_MACHINE_BIT);
        Duration duration = parseSafeGuardDuration(safeGuardDuration);
        return leaseManager.acquire(namespace, new InstanceId(instanceId, stable), machineBit, duration).toMachineState();
    }
    
    /**
     * Revert a machine ID, the operation is idempotent.
     */
    @Override
    @Operation(summary = "Revert a machine ID, the operation is idempotent.", deprecated = true)
    public void revert(@PathVariable String namespace, String instanceId, boolean stable, @Nullable Integer machineId, @Nullable Long lastTimeStamp) {
        checkNamespaceAndInstanceId(namespace, instanceId);
        InstanceId instance = new InstanceId(instanceId, stable);
        MachineState machineState = clientMachineState(machineId, lastTimeStamp);
        Optional<Lease> lease = leaseManager.reclaim(namespace, instance)
            .filter(held -> machineState == null || held.getMachineId() == machineState.getMachineId());
        if (lease.isEmpty()) {
            if (log.isWarnEnabled()) {
                log.warn("Revert [{}] @ namespace:[{}] - no lease held, the machine id may have been taken over.", instance, namespace);
            }
            return;
        }
        long watermark = machineState == null ? lease.get().getWatermark() : Math.max(lease.get().getWatermark(), machineState.getLastTimeStamp());
        if (!leaseManager.release(lease.get(), watermark) && log.isWarnEnabled()) {
            log.warn("Revert [{}] - taken over in the meantime, skip release.", lease.get());
        }
    }
    
    /**
     * Guard a machine ID.
     */
    @Override
    @Operation(summary = "Guard a machine ID.", deprecated = true)
    public void guard(@PathVariable String namespace, String instanceId, boolean stable, String safeGuardDuration, @Nullable Integer machineId, @Nullable Long lastTimeStamp) throws MachineIdLostException {
        checkNamespaceAndInstanceId(namespace, instanceId);
        parseSafeGuardDuration(safeGuardDuration);
        InstanceId instance = new InstanceId(instanceId, stable);
        MachineState machineState = clientMachineState(machineId, lastTimeStamp);
        Optional<Lease> lease = leaseManager.reclaim(namespace, instance);
        if (lease.isEmpty() || (machineState != null && lease.get().getMachineId() != machineState.getMachineId())) {
            throw new MachineIdLostException(namespace, instance, machineState);
        }
    }
    
    private static void checkNamespaceAndInstanceId(String namespace, String instanceId) {
        Checks.checkArgument(!Checks.isNullOrEmpty(namespace) && !namespace.isBlank(), "namespace can not be blank!");
        Checks.checkArgument(!Checks.isNullOrEmpty(instanceId) && !instanceId.isBlank(), "instanceId can not be blank!");
    }
    
    private static Duration parseSafeGuardDuration(String safeGuardDuration) {
        Duration duration;
        try {
            duration = Duration.parse(safeGuardDuration);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException(Checks.lenientFormat("safeGuardDuration:[%s] is not an ISO-8601 duration!", safeGuardDuration), e);
        }
        Checks.checkArgument(!duration.isNegative(), "safeGuardDuration:[%s] can not be negative!", safeGuardDuration);
        return duration;
    }
    
    @Nullable
    private MachineState clientMachineState(@Nullable Integer machineId, @Nullable Long lastTimeStamp) {
        Checks.checkArgument((machineId == null) == (lastTimeStamp == null), "machineId and lastTimeStamp must be provided together!");
        if (machineId == null) {
            return null;
        }
        Checks.checkArgument(machineId >= 0, "machineId:[%s] must be greater than or equal to 0!", machineId);
        // A timestamp far ahead would keep the machine id from ever being recycled, or make its next owner wait.
        if (maxClockSkew != null && !maxClockSkew.isZero() && !maxClockSkew.isNegative()) {
            long latest = System.currentTimeMillis() + maxClockSkew.toMillis();
            Checks.checkArgument(lastTimeStamp <= latest, "lastTimeStamp:[%s] is more than [%s] ahead of the server clock!", lastTimeStamp, maxClockSkew);
        }
        return MachineState.of(machineId, lastTimeStamp);
    }
}
