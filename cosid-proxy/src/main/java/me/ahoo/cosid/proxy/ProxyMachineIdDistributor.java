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

import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.machine.AbstractMachineIdDistributor;
import me.ahoo.cosid.snowflake.machine.InstanceId;
import me.ahoo.cosid.snowflake.machine.MachineIdLostException;
import me.ahoo.cosid.snowflake.machine.MachineIdOverflowException;
import me.ahoo.cosid.snowflake.machine.MachineState;
import me.ahoo.cosid.snowflake.machine.MachineStateStorage;
import me.ahoo.cosid.snowflake.machine.NotFoundMachineStateException;

import com.google.common.base.Strings;
import lombok.SneakyThrows;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.time.Duration;

/**
 * ProxyMachineIdDistributor .
 *
 * @author ahoo wang
 */
public class ProxyMachineIdDistributor extends AbstractMachineIdDistributor {
    
    public static final MediaType JSON
        = MediaType.get("application/json; charset=utf-8");
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
        
        Request request = new Request.Builder()
            .url(apiUrl)
            .post(RequestBody.create(JSON, ""))
            .build();
        try (Response response = client.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            assert responseBody != null;
            if (response.isSuccessful()) {
                return Jsons.OBJECT_MAPPER.readValue(responseBody.bytes(), MachineStateDto.class);
            }
            ErrorResponse errorResponse = Jsons.OBJECT_MAPPER.readValue(responseBody.bytes(), ErrorResponse.class);
            switch (errorResponse.getCode()) {
                case "M-01":
                    throw new MachineIdOverflowException(machineBit, instanceId);
                default:
                    throw new IllegalStateException("Unexpected value: " + errorResponse.getCode());
            }
        }
    }
    
    @SneakyThrows
    @Override
    protected void revertRemote(String namespace, InstanceId instanceId, MachineState machineState) {
        String apiUrl = Strings.lenientFormat("%s/machines/%s?instanceId=%s&stable=%s", proxyHost, namespace, instanceId.getInstanceId(), instanceId.isStable());
        
        Request request = new Request.Builder()
            .url(apiUrl)
            .delete()
            .build();
        try (Response response = client.newCall(request).execute()) {
            //ignored
        }
    }
    
    @SneakyThrows
    @Override
    protected void guardRemote(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) {
        String apiUrl =
            Strings.lenientFormat("%s/machines/%s?instanceId=%s&stable=%s&safeGuardDuration=%s", proxyHost, namespace, instanceId.getInstanceId(), instanceId.isStable(), safeGuardDuration);
        
        Request request = new Request.Builder()
            .url(apiUrl)
            .patch(RequestBody.create(JSON, ""))
            .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return;
            }
            ResponseBody responseBody = response.body();
            assert responseBody != null;
            ErrorResponse errorResponse = Jsons.OBJECT_MAPPER.readValue(responseBody.bytes(), ErrorResponse.class);
            switch (errorResponse.getCode()) {
                case "M-02":
                    throw new NotFoundMachineStateException(namespace, instanceId);
                case "M-03":
                    throw new MachineIdLostException(namespace, instanceId, machineState);
                default:
                    throw new IllegalStateException("Unexpected value: " + errorResponse.getCode());
            }
        }
    }
    
}
