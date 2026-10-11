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

package me.ahoo.cosid.proxy.server.contract;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.proxy.ProxyMachineIdDistributor;
import me.ahoo.cosid.proxy.api.MachineClient;
import me.ahoo.cosid.proxy.server.ProxyServer;
import me.ahoo.cosid.test.machine.distributor.MachineIdDistributorSpec;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Runs {@link MachineIdDistributorSpec} through {@link ProxyMachineIdDistributor} against a real proxy server.
 */
@EnabledIf("me.ahoo.cosid.proxy.server.contract.ProxyEndToEnd#isAvailable")
@SpringBootTest(classes = ProxyServer.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProxyMachineIdDistributorEndToEndTest extends MachineIdDistributorSpec {
    @LocalServerPort
    int port;
    MachineClient machineClient;
    
    @DynamicPropertySource
    static void serverProperties(DynamicPropertyRegistry registry) {
        ProxyEndToEnd.serverProperties(registry);
    }
    
    @BeforeEach
    void setup() {
        machineClient = ProxyEndToEnd.machineClient(port);
    }
    
    @Override
    protected MachineIdDistributor getDistributor() {
        return new ProxyMachineIdDistributor(machineClient, MachineStateStorage.IN_MEMORY, ClockBackwardsSynchronizer.DEFAULT);
    }
    
    @Test
    void serverRejectsClientsWithoutToken() {
        MachineClient anonymous = ProxyEndToEnd.anonymousMachineClient(port);
        
        Assertions.assertThrows(HttpClientErrorException.Unauthorized.class,
            () -> anonymous.distribute("e2e", 4, "anonymous", false, "PT1M"));
    }
}
