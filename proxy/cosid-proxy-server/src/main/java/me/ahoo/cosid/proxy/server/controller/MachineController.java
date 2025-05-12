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
    public MachineState distribute(@PathVariable String namespace, int machineBit, InstanceId instanceId, String safeGuardDuration) throws MachineIdOverflowException {
        return distributor.distribute(namespace, machineBit, instanceId, Duration.parse(safeGuardDuration));
    }

    /**
     * Revert a machine ID, the operation is idempotent.
     */
    @Override
    @Operation(summary = "Revert a machine ID, the operation is idempotent.")
    public void revert(@PathVariable String namespace, InstanceId instanceId) {
        distributor.revert(namespace, instanceId);
    }

    /**
     * Guard a machine ID.
     */
    @Override
    @Operation(summary = "Guard a machine ID.")
    public void guard(@PathVariable String namespace, InstanceId instanceId, String safeGuardDuration) throws MachineIdLostException {
        distributor.guard(namespace, instanceId, Duration.parse(safeGuardDuration));
    }
}
