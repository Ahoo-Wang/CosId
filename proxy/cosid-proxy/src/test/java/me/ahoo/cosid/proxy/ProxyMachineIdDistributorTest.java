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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.InMemoryMachineStateStorage;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdOverflowException;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.NotFoundMachineStateException;
import me.ahoo.cosid.proxy.api.ErrorResponse;
import me.ahoo.cosid.proxy.api.MachineClient;
import me.ahoo.cosid.proxy.api.MachineStateResponse;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

class ProxyMachineIdDistributorTest {

    @Test
    void distributeDelegatesToMachineClientAndStoresReturnedState() {
        RecordingMachineClient machineClient = new RecordingMachineClient();
        MachineStateStorage machineStateStorage = new InMemoryMachineStateStorage();
        MachineIdDistributor distributor = new ProxyMachineIdDistributor(machineClient, machineStateStorage, ClockBackwardsSynchronizer.DEFAULT);
        InstanceId instanceId = InstanceId.of("127.0.0.1", 8080, false);

        MachineState machineState = distributor.distribute("test_namespace", 8, instanceId, Duration.ofSeconds(30));

        assertEquals(7, machineState.getMachineId());
        assertEquals("test_namespace", machineClient.distributeNamespace);
        assertEquals(8, machineClient.distributeMachineBit);
        assertEquals(instanceId.getInstanceId(), machineClient.distributeInstanceId);
        assertFalse(machineClient.distributeStable);
        assertEquals("PT30S", machineClient.distributeSafeGuardDuration);
        assertEquals(7, machineStateStorage.get("test_namespace", instanceId).getMachineId());
    }

    @Test
    void distributeMapsOverflowErrorResponseToDomainException() {
        RecordingMachineClient machineClient = new RecordingMachineClient();
        machineClient.distributeFailure = badRequest(ErrorResponse.MACHINE_ID_OVERFLOW, "overflow");
        MachineIdDistributor distributor = new ProxyMachineIdDistributor(machineClient, new InMemoryMachineStateStorage(), ClockBackwardsSynchronizer.DEFAULT);

        assertThrows(MachineIdOverflowException.class,
            () -> distributor.distribute("test_namespace", 1, InstanceId.of("instance", false), Duration.ofSeconds(30)));
    }

    @Test
    public void guardMapsNotFoundMachineStateErrorResponseToDomainException() {
        RecordingMachineClient machineClient = new RecordingMachineClient();
        machineClient.guardFailure = badRequest(ErrorResponse.NOT_FOUND_MACHINE_STATE, "not found");
        MachineStateStorage machineStateStorage = new InMemoryMachineStateStorage();
        MachineIdDistributor distributor = new ProxyMachineIdDistributor(machineClient, machineStateStorage, ClockBackwardsSynchronizer.DEFAULT);
        InstanceId instanceId = InstanceId.of("instance", false);
        machineStateStorage.set("test_namespace", 10, instanceId);

        assertThrows(NotFoundMachineStateException.class,
            () -> distributor.guard("test_namespace", instanceId, MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION));
    }

    @Test
    public void guardMapsMachineIdLostErrorResponseToDomainException() {
        RecordingMachineClient machineClient = new RecordingMachineClient();
        machineClient.guardFailure = badRequest(ErrorResponse.MACHINE_ID_LOST, "lost");
        MachineStateStorage machineStateStorage = new InMemoryMachineStateStorage();
        MachineIdDistributor distributor = new ProxyMachineIdDistributor(machineClient, machineStateStorage, ClockBackwardsSynchronizer.DEFAULT);
        InstanceId instanceId = InstanceId.of("instance", false);
        machineStateStorage.set("test_namespace", 10, instanceId);

        assertThrows(MachineIdLostException.class,
            () -> distributor.guard("test_namespace", instanceId, MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION));
    }

    @Test
    public void revertDelegatesToMachineClientWhenLocalStateExists() {
        RecordingMachineClient machineClient = new RecordingMachineClient();
        MachineStateStorage machineStateStorage = new InMemoryMachineStateStorage();
        MachineIdDistributor distributor = new ProxyMachineIdDistributor(machineClient, machineStateStorage, ClockBackwardsSynchronizer.DEFAULT);
        InstanceId instanceId = InstanceId.of("instance", true);
        machineStateStorage.set("test_namespace", 3, instanceId);

        distributor.revert("test_namespace", instanceId);

        assertEquals("test_namespace", machineClient.revertNamespace);
        assertEquals("instance", machineClient.revertInstanceId);
        assertTrue(machineClient.revertStable);
    }

    private static HttpClientErrorException badRequest(String code, String message) {
        String responseBody = "{\"code\":\"" + code + "\",\"msg\":\"" + message + "\",\"errors\":[]}";
        return HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST,
            "Bad Request",
            HttpHeaders.EMPTY,
            responseBody.getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8
        );
    }

    private static class RecordingMachineClient implements MachineClient {
        private String distributeNamespace;
        private int distributeMachineBit;
        private String distributeInstanceId;
        private boolean distributeStable;
        private String distributeSafeGuardDuration;
        private HttpClientErrorException distributeFailure;
        private String revertNamespace;
        private String revertInstanceId;
        private boolean revertStable;
        private HttpClientErrorException guardFailure;

        @Override
        public MachineStateResponse distribute(String namespace, int machineBit, String instanceId, boolean stable, String safeGuardDuration) {
            if (distributeFailure != null) {
                throw distributeFailure;
            }
            this.distributeNamespace = namespace;
            this.distributeMachineBit = machineBit;
            this.distributeInstanceId = instanceId;
            this.distributeStable = stable;
            this.distributeSafeGuardDuration = safeGuardDuration;
            return new MachineStateResponse(7, System.currentTimeMillis());
        }

        @Override
        public void revert(String namespace, String instanceId, boolean stable) {
            this.revertNamespace = namespace;
            this.revertInstanceId = instanceId;
            this.revertStable = stable;
        }

        @Override
        public void guard(String namespace, String instanceId, boolean stable, String safeGuardDuration) {
            if (guardFailure != null) {
                throw guardFailure;
            }
        }
    }
}
