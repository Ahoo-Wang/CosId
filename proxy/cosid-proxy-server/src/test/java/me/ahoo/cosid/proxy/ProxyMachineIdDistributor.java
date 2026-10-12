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

package me.ahoo.cosid.proxy;

import static me.ahoo.cosid.proxy.api.ErrorResponse.MACHINE_ID_LOST;
import static me.ahoo.cosid.proxy.api.ErrorResponse.MACHINE_ID_OVERFLOW;
import static me.ahoo.cosid.proxy.api.ErrorResponse.NOT_FOUND_MACHINE_STATE;

import me.ahoo.cosid.internal.Checks;
import me.ahoo.cosid.machine.AbstractMachineIdDistributor;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineIdOverflowException;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.NotFoundMachineStateException;
import me.ahoo.cosid.proxy.api.ErrorResponse;
import me.ahoo.cosid.proxy.api.MachineClient;

import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;

/**
 * The 3.x proxy client (the version 1 machine API), kept as a test fixture: tests run 3.x clients against a 4.0 server.
 * Removed from the library in 4.0, see {@code ProxyMachineLeaseStore}.
 */
public class ProxyMachineIdDistributor extends AbstractMachineIdDistributor {

    private final MachineClient machineClient;

    public ProxyMachineIdDistributor(MachineClient machineClient, MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        super(machineStateStorage, clockBackwardsSynchronizer);
        this.machineClient = machineClient;
    }

    @Override
    protected MachineState distributeRemote(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
        try {
            return machineClient.distribute(namespace, machineBit, instanceId.getInstanceId(), instanceId.isStable(), safeGuardDuration.toString());
        } catch (HttpClientErrorException.BadRequest badRequest) {
            ErrorResponse errorResponse = Jsons.OBJECT_MAPPER.readValue(badRequest.getResponseBodyAsByteArray(), ErrorResponse.class);
            if (errorResponse.getCode().equals(MACHINE_ID_OVERFLOW)) {
                throw new MachineIdOverflowException(machineBit, instanceId);
            }
            throw new IllegalStateException(Checks.lenientFormat("Unexpected code:[%s] - message:[%s].", errorResponse.getCode(), errorResponse.getMsg()));
        }
    }

    @Override
    protected void revertRemote(String namespace, InstanceId instanceId, MachineState machineState) {
        machineClient.revert(namespace, instanceId.getInstanceId(), instanceId.isStable(), machineState.getMachineId(), machineState.getLastTimeStamp());
    }

    @Override
    protected void guardRemote(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) {
        try {
            machineClient.guard(namespace, instanceId.getInstanceId(), instanceId.isStable(), safeGuardDuration.toString(), machineState.getMachineId(), machineState.getLastTimeStamp());
        } catch (HttpClientErrorException.BadRequest badRequest) {
            ErrorResponse errorResponse = Jsons.OBJECT_MAPPER.readValue(badRequest.getResponseBodyAsByteArray(), ErrorResponse.class);
            switch (errorResponse.getCode()) {
                case NOT_FOUND_MACHINE_STATE -> throw new NotFoundMachineStateException(namespace, instanceId);
                case MACHINE_ID_LOST -> throw new MachineIdLostException(namespace, instanceId, machineState);
                // e.g. a lastTimeStamp rejected by cosid.proxy.server.max-clock-skew: keep the server's reason.
                default -> throw new IllegalStateException(Checks.lenientFormat("Unexpected code:[%s] - message:[%s].", errorResponse.getCode(), errorResponse.getMsg()));
            }
        }

    }

}
