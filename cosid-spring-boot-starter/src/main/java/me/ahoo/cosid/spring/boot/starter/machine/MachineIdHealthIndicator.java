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

package me.ahoo.cosid.spring.boot.starter.machine;

import me.ahoo.cosid.machine.GuardDistribute;
import me.ahoo.cosid.machine.GuardianState;
import me.ahoo.cosid.machine.MachineIdGuarder;
import me.ahoo.cosid.machine.MachineIdLease;
import me.ahoo.cosid.machine.NamespacedInstanceId;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A Spring Boot Actuator HealthIndicator that monitors the health of machine ID guarding.
 * This indicator checks the status of all guardian states managed by the MachineIdGuarder
 * and reports the overall health of the machine ID distribution system.
 *
 * <p>
 * The health check returns UP if all guardian states are healthy (not failed),
 * or DOWN if any guardian state has failed, including details about the failure.
 * </p>
 */
public class MachineIdHealthIndicator implements HealthIndicator {
    private final MachineIdGuarder machineIdGuarder;
    @Nullable
    private final GuardDistribute guardDistribute;

    public MachineIdHealthIndicator(MachineIdGuarder machineIdGuarder) {
        this(machineIdGuarder, null);
    }
    
    /**
     * Create an indicator that also shows the machine id of each lease.
     */
    public MachineIdHealthIndicator(MachineIdGuarder machineIdGuarder, @Nullable GuardDistribute guardDistribute) {
        this.machineIdGuarder = machineIdGuarder;
        this.guardDistribute = guardDistribute;
    }

    /**
     * {@code DOWN} when a guard failed, or a lease is lost or expired: a lost lease stays lost even after a later renewal
     * succeeds, and its generators throw. Each lease is listed with its machine id, instance id, remaining validity and
     * whether it is lost or released.
     */
    @Override
    public Health health() {
        Map<String, Object> leases = new LinkedHashMap<>();
        boolean leaseDown = false;
        long now = System.currentTimeMillis();
        Map<NamespacedInstanceId, Integer> machineIds = guardDistribute == null ? Map.of() : guardDistribute.getMachineIds();
        for (Map.Entry<NamespacedInstanceId, MachineIdLease> entry : machineIdGuarder.getLeases().entrySet()) {
            NamespacedInstanceId key = entry.getKey();
            MachineIdLease lease = entry.getValue();
            boolean expired = !lease.isReleased() && !lease.isLost() && !lease.isValid(now);
            leaseDown |= lease.isLost() || expired;
            Map<String, Object> detail = new LinkedHashMap<>();
            Integer machineId = machineIds.get(key);
            if (machineId != null) {
                detail.put("machineId", machineId);
            }
            detail.put("instanceId", key.getInstanceId().getInstanceId());
            detail.put("remainingMillis", lease.getValidUntil() == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0, lease.getValidUntil() - now));
            detail.put("lost", lease.isLost());
            detail.put("expired", expired);
            detail.put("released", lease.isReleased());
            String name = leases.containsKey(key.getNamespace()) ? key.getNamespace() + "@" + key.getInstanceId().getInstanceId() : key.getNamespace();
            leases.put(name, detail);
        }
        
        Optional<GuardianState> firstFailedGuardStateOp = machineIdGuarder.getGuardianStates().values().stream().filter(GuardianState::isFailed).findFirst();
        Health.Builder builder;
        if (firstFailedGuardStateOp.isPresent()) {
            GuardianState firstErrorGuardState = firstFailedGuardStateOp.get();
            builder = Health.down(firstErrorGuardState.getError()).withDetail("guardAt", firstErrorGuardState.getGuardAt());
        } else {
            builder = leaseDown ? Health.down() : Health.up();
        }
        if (!leases.isEmpty()) {
            builder.withDetail("leases", leases);
        }
        return builder.build();
    }
}
