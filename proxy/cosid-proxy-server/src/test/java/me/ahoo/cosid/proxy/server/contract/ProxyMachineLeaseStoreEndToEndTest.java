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

import me.ahoo.cosid.machine.lease.MachineLeaseStore;
import me.ahoo.cosid.proxy.ProxyMachineLeaseStore;
import me.ahoo.cosid.proxy.server.ProxyServer;
import me.ahoo.cosid.test.machine.lease.MachineLeaseStoreSpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Runs {@link MachineLeaseStoreSpec} through {@link ProxyMachineLeaseStore} (the version 2 lease API) against a real
 * proxy server on Redis.
 */
@EnabledIf("me.ahoo.cosid.proxy.server.contract.ProxyEndToEnd#isAvailable")
@SpringBootTest(classes = ProxyServer.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProxyMachineLeaseStoreEndToEndTest extends MachineLeaseStoreSpec {
    @LocalServerPort
    int port;
    ProxyMachineLeaseStore store;
    
    @DynamicPropertySource
    static void serverProperties(DynamicPropertyRegistry registry) {
        ProxyEndToEnd.serverProperties(registry);
    }
    
    @BeforeEach
    void setup() {
        store = new ProxyMachineLeaseStore(ProxyEndToEnd.machineLeaseClient(port));
    }
    
    @Override
    protected MachineLeaseStore getStore() {
        return store;
    }
}
