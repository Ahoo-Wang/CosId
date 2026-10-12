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
import me.ahoo.cosid.machine.InMemoryMachineStateStorage;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.proxy.ProxyMachineIdDistributor;
import me.ahoo.cosid.proxy.ProxyMachineLeaseStore;
import me.ahoo.cosid.proxy.server.ProxyServer;
import me.ahoo.cosid.test.machine.lease.MixedLeaseFormatSpec;

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 3.x proxy clients (the version 1 machine API) and 4.0 proxy clients (the version 2 lease API) on one server and
 * namespace never hold the same machine id: {@link MixedLeaseFormatSpec} with {@link ProxyMachineIdDistributor} as the
 * 3.x instances.
 */
@EnabledIf("me.ahoo.cosid.proxy.server.contract.ProxyEndToEnd#isAvailable")
@SpringBootTest(classes = ProxyServer.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProxyMixedClientEndToEndTest extends MixedLeaseFormatSpec {
    @LocalServerPort
    int port;
    @Autowired
    StringRedisTemplate redisTemplate;
    
    @DynamicPropertySource
    static void serverProperties(DynamicPropertyRegistry registry) {
        ProxyEndToEnd.serverProperties(registry);
    }
    
    @SuppressWarnings("deprecation")
    @Override
    protected MachineIdDistributor v34() {
        return new ProxyMachineIdDistributor(ProxyEndToEnd.machineClient(port), new InMemoryMachineStateStorage(), ClockBackwardsSynchronizer.DEFAULT);
    }
    
    @Override
    protected MachineIdDistributor v40() {
        return new LeaseStoreMachineIdDistributor(new ProxyMachineLeaseStore(ProxyEndToEnd.machineLeaseClient(port)), new InMemoryMachineStateStorage(),
            ClockBackwardsSynchronizer.DEFAULT);
    }
    
    /**
     * The server's backend is Redis: a lagging 3.4 instance moves the machine id from {@code revert} to {@code itc_idx}
     * with the same timestamp.
     */
    @Override
    protected void takeReleasedAs34WithTheSameTimestamp(String namespace, int machineId, String instanceId) {
        String prefix = "cosid:{" + namespace + "}:";
        String legacy = (String) redisTemplate.opsForHash().get(prefix + "revert", String.valueOf(machineId));
        redisTemplate.opsForHash().delete(prefix + "revert", String.valueOf(machineId));
        redisTemplate.opsForHash().put(prefix + "itc_idx", instanceId, machineId + "|" + legacy);
    }
}
