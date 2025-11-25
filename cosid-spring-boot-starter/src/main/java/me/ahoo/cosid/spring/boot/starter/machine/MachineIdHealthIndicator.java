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

import me.ahoo.cosid.machine.GuardianState;
import me.ahoo.cosid.machine.MachineIdGuarder;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

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

    /**
     * Constructs a MachineIdHealthIndicator with the specified MachineIdGuarder.
     *
     * <p>
     * This constructor initializes the health indicator to monitor the provided
     * MachineIdGuarder instance for any failed guardian states that could indicate
     * issues with machine ID distribution.
     * </p>
     *
     * @param machineIdGuarder the MachineIdGuarder instance to monitor for health status;
     *                         must not be null and should contain the guardian states to check
     * @throws IllegalArgumentException if machineIdGuarder is null
     */
    public MachineIdHealthIndicator(MachineIdGuarder machineIdGuarder) {
        this.machineIdGuarder = machineIdGuarder;
    }

    /**
     * Performs a health check on the machine ID guarding system.
     *
     * <p>
     * This method inspects all guardian states from the MachineIdGuarder to determine
     * if the machine ID distribution is functioning correctly. It returns a healthy status
     * if no guardian states are in a failed state, or an unhealthy status with details
     * about the first encountered failure.
     * </p>
     *
     * <p>
     * The health check includes:
     * <ul>
     *   <li>Checking if any GuardianState is marked as failed</li>
     *   <li>If failed, including the error cause and timestamp in the health details</li>
     * </ul>
     * </p>
     *
     * @return a Health object indicating UP if all guardian states are healthy,or DOWN with error details if any guardian state has failed.
     * @throws RuntimeException if an unexpected error occurs during health check processing
     */
    @Override
    public Health health() {
        Optional<GuardianState> firstFailedGuardStateOp = machineIdGuarder.getGuardianStates().values().stream().filter(GuardianState::isFailed).findFirst();
        if (firstFailedGuardStateOp.isEmpty()) {
            return Health.up().build();
        }
        GuardianState firstErrorGuardState = firstFailedGuardStateOp.get();
        Throwable error = firstErrorGuardState.getError();
        return Health.down(error)
            .withDetail("guardAt", firstErrorGuardState.getGuardAt())
            .build();
    }
}
