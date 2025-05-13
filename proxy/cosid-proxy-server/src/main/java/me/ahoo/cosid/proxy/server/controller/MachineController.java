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

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineIdOverflowException;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.proxy.api.MachineApi;
import me.ahoo.cosid.proxy.api.MachineStateResponse;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Machine resource controller .
 * Used for snowflake algorithm machine number distribution.
 *
 * @author ahoo wang
 */
@RestController
public class MachineController implements MachineApi {
    private final MachineIdDistributor distributor;

    public MachineController(MachineIdDistributor distributor) {
        this.distributor = distributor;
    }

    /**
     * Distribute a machine ID, the operation is idempotent.
     */
    @Override
    @Operation(summary = "Distribute a machine ID, the operation is idempotent.")
    public MachineStateResponse distribute(@PathVariable String namespace, int machineBit, String instanceId, boolean stable, String safeGuardDuration) throws MachineIdOverflowException {
        MachineState machineState = distributor.distribute(namespace, machineBit, new InstanceId(instanceId, stable), Duration.parse(safeGuardDuration));
        return new MachineStateResponse(machineState.getMachineId(), machineState.getLastTimeStamp());
    }

    /**
     * Revert a machine ID, the operation is idempotent.
     */
    @Override
    @Operation(summary = "Revert a machine ID, the operation is idempotent.")
    public void revert(@PathVariable String namespace, String instanceId, boolean stable) {
        distributor.revert(namespace, new InstanceId(instanceId, stable));
    }

    /**
     * Guard a machine ID.
     */
    @Override
    @Operation(summary = "Guard a machine ID.")
    public void guard(@PathVariable String namespace, String instanceId, boolean stable, String safeGuardDuration) throws MachineIdLostException {
        distributor.guard(namespace, new InstanceId(instanceId, stable), Duration.parse(safeGuardDuration));
    }
}
