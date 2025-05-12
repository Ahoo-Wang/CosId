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

import me.ahoo.cosid.machine.AbstractMachineIdDistributor;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineIdOverflowException;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.NotFoundMachineStateException;
import me.ahoo.cosid.proxy.api.ErrorResponse;
import me.ahoo.cosid.proxy.api.MachineApi;

import com.google.common.base.Strings;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;

/**
 * ProxyMachineIdDistributor .
 *
 * <p><img src="doc-files/CosId-Proxy.png" alt="CosId-Proxy"></p>
 *
 * @author ahoo wang
 */
@Slf4j
public class ProxyMachineIdDistributor extends AbstractMachineIdDistributor {

    private final MachineApi machineApi;

    public ProxyMachineIdDistributor(MachineApi machineApi, MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        super(machineStateStorage, clockBackwardsSynchronizer);
        this.machineApi = machineApi;
    }

    @SneakyThrows
    @Override
    protected MachineState distributeRemote(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
        if (log.isInfoEnabled()) {
            log.info("Distribute Remote instanceId:[{}] - machineBit:[{}] @ namespace:[{}].", instanceId, machineBit, namespace);
        }
        try {
            return machineApi.distribute(namespace, machineBit, instanceId.getInstanceId(), instanceId.isStable(), safeGuardDuration.toString());
        } catch (HttpClientErrorException.BadRequest badRequest) {
            ErrorResponse errorResponse = Jsons.OBJECT_MAPPER.readValue(badRequest.getResponseBodyAsByteArray(), ErrorResponse.class);
            if (errorResponse.getCode().equals(MACHINE_ID_OVERFLOW)) {
                throw new MachineIdOverflowException(machineBit, instanceId);
            }
            throw new IllegalStateException(Strings.lenientFormat("Unexpected code:[%s] - message:[%s].", errorResponse.getCode(), errorResponse.getMsg()));
        }
    }

    @SneakyThrows
    @Override
    protected void revertRemote(String namespace, InstanceId instanceId, MachineState machineState) {
        if (log.isInfoEnabled()) {
            log.info("Revert Remote [{}] instanceId:[{}] @ namespace:[{}].", machineState, instanceId, namespace);
        }
        machineApi.revert(namespace, instanceId.getInstanceId(), instanceId.isStable());
    }

    @SneakyThrows
    @Override
    protected void guardRemote(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) {
        if (log.isInfoEnabled()) {
            log.info("Guard Remote [{}] instanceId:[{}] @ namespace:[{}].", machineState, instanceId, namespace);
        }
        try {
            machineApi.guard(namespace, instanceId.getInstanceId(), instanceId.isStable(), safeGuardDuration.toString());
        } catch (HttpClientErrorException.BadRequest badRequest) {
            ErrorResponse errorResponse = Jsons.OBJECT_MAPPER.readValue(badRequest.getResponseBodyAsByteArray(), ErrorResponse.class);
            switch (errorResponse.getCode()) {
                case NOT_FOUND_MACHINE_STATE -> throw new NotFoundMachineStateException(namespace, instanceId);
                case MACHINE_ID_LOST -> throw new MachineIdLostException(namespace, instanceId, machineState);
                default -> throw new IllegalStateException("Unexpected value: " + errorResponse.getCode());
            }
        }

    }

}
