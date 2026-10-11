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

import me.ahoo.cosid.machine.AbstractMachineIdDistributor;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineIdOverflowException;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.StatelessMachineIdDistributor;
import me.ahoo.cosid.proxy.api.MachineApi;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.format.DateTimeParseException;

/**
 * Machine resource controller .
 * Used for snowflake algorithm machine number distribution.
 *
 * <p>Stateless: the server passes every call straight to the machine-id store and never caches a client's
 * machine state, so it survives restarts and can run as several nodes behind a load balancer. Clients send
 * their own {@code machineId} and {@code lastTimeStamp} with guard and revert.
 *
 * @author ahoo wang
 */
@Slf4j
@RestController
public class MachineController implements MachineApi {
    private static final int MAX_MACHINE_BIT = Integer.SIZE - 1;
    private final MachineIdDistributor distributor;
    @Nullable
    private final StatelessMachineIdDistributor statelessDistributor;

    /**
     * Creates the controller.
     *
     * @param distributor the store-backed distributor
     * @param machineStateStorage the same storage {@code distributor} uses, kept up to date only for older clients
     */
    public MachineController(MachineIdDistributor distributor, MachineStateStorage machineStateStorage) {
        this.distributor = distributor;
        this.statelessDistributor = distributor instanceof AbstractMachineIdDistributor abstractDistributor
            ? new StatelessMachineIdDistributor(abstractDistributor, machineStateStorage) : null;
    }

    /**
     * Distribute a machine ID, the operation is idempotent.
     */
    @Override
    @Operation(summary = "Distribute a machine ID, the operation is idempotent.")
    public MachineState distribute(@PathVariable String namespace, int machineBit, String instanceId, boolean stable, String safeGuardDuration) throws MachineIdOverflowException {
        checkNamespaceAndInstanceId(namespace, instanceId);
        Preconditions.checkArgument(machineBit > 0 && machineBit <= MAX_MACHINE_BIT, "machineBit:[%s] must be in [1, %s]!", machineBit, MAX_MACHINE_BIT);
        Duration duration = parseSafeGuardDuration(safeGuardDuration);
        InstanceId instance = new InstanceId(instanceId, stable);
        if (statelessDistributor == null) {
            return distributor.distribute(namespace, machineBit, instance, duration);
        }
        return statelessDistributor.distribute(namespace, machineBit, instance, duration);
    }

    /**
     * Revert a machine ID, the operation is idempotent.
     */
    @Override
    @Operation(summary = "Revert a machine ID, the operation is idempotent.")
    public void revert(@PathVariable String namespace, String instanceId, boolean stable, @Nullable Integer machineId, @Nullable Long lastTimeStamp) {
        checkNamespaceAndInstanceId(namespace, instanceId);
        InstanceId instance = new InstanceId(instanceId, stable);
        MachineState machineState = clientMachineState(machineId, lastTimeStamp);
        if (statelessDistributor == null || machineState == null) {
            legacyStatefulCall("revert", namespace, instance);
            distributor.revert(namespace, instance);
            return;
        }
        statelessDistributor.revert(namespace, instance, machineState);
    }

    /**
     * Guard a machine ID.
     */
    @Override
    @Operation(summary = "Guard a machine ID.")
    public void guard(@PathVariable String namespace, String instanceId, boolean stable, String safeGuardDuration, @Nullable Integer machineId, @Nullable Long lastTimeStamp) throws MachineIdLostException {
        checkNamespaceAndInstanceId(namespace, instanceId);
        Duration duration = parseSafeGuardDuration(safeGuardDuration);
        InstanceId instance = new InstanceId(instanceId, stable);
        MachineState machineState = clientMachineState(machineId, lastTimeStamp);
        if (statelessDistributor == null || machineState == null) {
            legacyStatefulCall("guard", namespace, instance);
            distributor.guard(namespace, instance, duration);
            return;
        }
        statelessDistributor.guard(namespace, instance, machineState, duration);
    }

    private static void checkNamespaceAndInstanceId(String namespace, String instanceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace) && !namespace.isBlank(), "namespace can not be blank!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(instanceId) && !instanceId.isBlank(), "instanceId can not be blank!");
    }

    private static Duration parseSafeGuardDuration(String safeGuardDuration) {
        Duration duration;
        try {
            duration = Duration.parse(safeGuardDuration);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException(Strings.lenientFormat("safeGuardDuration:[%s] is not an ISO-8601 duration!", safeGuardDuration), e);
        }
        Preconditions.checkArgument(!duration.isNegative(), "safeGuardDuration:[%s] can not be negative!", safeGuardDuration);
        return duration;
    }

    @Nullable
    private static MachineState clientMachineState(@Nullable Integer machineId, @Nullable Long lastTimeStamp) {
        Preconditions.checkArgument((machineId == null) == (lastTimeStamp == null), "machineId and lastTimeStamp must be provided together!");
        if (machineId == null) {
            return null;
        }
        return MachineState.of(machineId, lastTimeStamp);
    }

    private static void legacyStatefulCall(String operation, String namespace, InstanceId instanceId) {
        if (log.isWarnEnabled()) {
            log.warn("Legacy stateful {} for instanceId:[{}] @ namespace:[{}]: the client did not send machineId/lastTimeStamp. "
                + "This path depends on the server's in-memory state and fails after a server restart or across server nodes; upgrade the client.",
                operation, instanceId, namespace);
        }
    }
}
