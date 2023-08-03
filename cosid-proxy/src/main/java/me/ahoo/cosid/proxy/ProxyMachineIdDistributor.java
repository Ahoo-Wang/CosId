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

import static me.ahoo.cosid.proxy.ErrorResponse.MACHINE_ID_LOST;
import static me.ahoo.cosid.proxy.ErrorResponse.MACHINE_ID_OVERFLOW;
import static me.ahoo.cosid.proxy.ErrorResponse.NOT_FOUND_MACHINE_STATE;

import me.ahoo.cosid.machine.AbstractMachineIdDistributor;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineIdOverflowException;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.NotFoundMachineStateException;

import com.google.common.base.Strings;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.Util;

import java.time.Duration;

/**
 * ProxyMachineIdDistributor .
 * <p><img src="doc-files/CosId-Proxy.png" alt="CosId-Proxy"></p>
 *
 * @author ahoo wang
 */
@Slf4j
public class ProxyMachineIdDistributor extends AbstractMachineIdDistributor {
    
    private final OkHttpClient client;
    
    private final String proxyHost;
    
    public ProxyMachineIdDistributor(OkHttpClient client, String proxyHost, MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        super(machineStateStorage, clockBackwardsSynchronizer);
        this.client = client;
        this.proxyHost = proxyHost;
    }
    
    @SneakyThrows
    @Override
    protected MachineState distributeRemote(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
        String apiUrl =
            Strings.lenientFormat("%s/machines/%s?instanceId=%s&stable=%s&machineBit=%s&safeGuardDuration=%s", proxyHost, namespace, instanceId.getInstanceId(), instanceId.isStable(), machineBit,
                safeGuardDuration);
        if (log.isInfoEnabled()) {
            log.info("Distribute Remote instanceId:[{}] - machineBit:[{}] @ namespace:[{}] - apiUrl:[{}].", instanceId, machineBit, namespace, apiUrl);
        }
        
        Request request = new Request.Builder()
            .url(apiUrl)
            .post(Util.EMPTY_REQUEST)
            .build();
        try (Response response = client.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            assert responseBody != null;
            String bodyStr = responseBody.string();
            if (log.isInfoEnabled()) {
                log.info("Distribute Remote instanceId:[{}] - machineBit:[{}] @ namespace:[{}] - response:[{}].", instanceId, machineBit, namespace, bodyStr);
            }
            
            if (response.isSuccessful()) {
                return Jsons.OBJECT_MAPPER.readValue(bodyStr, MachineStateDto.class);
            }
            ErrorResponse errorResponse = Jsons.OBJECT_MAPPER.readValue(bodyStr, ErrorResponse.class);
            if (errorResponse.getCode().equals(MACHINE_ID_OVERFLOW)) {
                throw new MachineIdOverflowException(machineBit, instanceId);
            }
            throw new IllegalStateException(Strings.lenientFormat("Unexpected code:[%s] - message:[%s].", errorResponse.getCode(), errorResponse.getMsg()));
        }
    }
    
    @SneakyThrows
    @Override
    protected void revertRemote(String namespace, InstanceId instanceId, MachineState machineState) {
        String apiUrl = Strings.lenientFormat("%s/machines/%s?instanceId=%s&stable=%s", proxyHost, namespace, instanceId.getInstanceId(), instanceId.isStable());
        if (log.isInfoEnabled()) {
            log.info("Revert Remote [{}] instanceId:[{}] @ namespace:[{}] - apiUrl:[{}].", machineState, instanceId, namespace, apiUrl);
        }
        
        Request request = new Request.Builder()
            .url(apiUrl)
            .delete()
            .build();
        try (Response response = client.newCall(request).execute()) {
            if (log.isInfoEnabled()) {
                ResponseBody responseBody = response.body();
                assert responseBody != null;
                String bodyStr = responseBody.string();
                log.info("Revert Remote [{}] instanceId:[{}] @ namespace:[{}] - response:[{}].", machineState, instanceId, namespace, bodyStr);
            }
        }
    }
    
    @SneakyThrows
    @Override
    protected void guardRemote(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) {
        String apiUrl =
            Strings.lenientFormat("%s/machines/%s?instanceId=%s&stable=%s&safeGuardDuration=%s", proxyHost, namespace, instanceId.getInstanceId(), instanceId.isStable(), safeGuardDuration);
        
        if (log.isInfoEnabled()) {
            log.info("Guard Remote [{}] instanceId:[{}] @ namespace:[{}] - apiUrl:[{}].", machineState, instanceId, namespace, apiUrl);
        }
        
        Request request = new Request.Builder()
            .url(apiUrl)
            .patch(Util.EMPTY_REQUEST)
            .build();
        try (Response response = client.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            assert responseBody != null;
            String bodyStr = responseBody.string();
            if (log.isInfoEnabled()) {
                log.info("Guard Remote [{}] instanceId:[{}] @ namespace:[{}] - response:[{}].", machineState, instanceId, namespace, bodyStr);
            }
            if (response.isSuccessful()) {
                return;
            }
            
            ErrorResponse errorResponse = Jsons.OBJECT_MAPPER.readValue(bodyStr, ErrorResponse.class);
            switch (errorResponse.getCode()) {
                case NOT_FOUND_MACHINE_STATE -> throw new NotFoundMachineStateException(namespace, instanceId);
                case MACHINE_ID_LOST -> throw new MachineIdLostException(namespace, instanceId, machineState);
                default -> throw new IllegalStateException("Unexpected value: " + errorResponse.getCode());
            }
        }
    }
    
}
