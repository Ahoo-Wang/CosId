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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.proxy.ProxyMachineIdDistributor;
import me.ahoo.cosid.proxy.server.ProxyServer;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdProxyMachineIdDistributorAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;
import me.ahoo.cosid.spring.boot.starter.segment.CosIdProxySegmentAutoConfiguration;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;

/**
 * Smoke test of the client side as the starter wires it (CoApi clients, {@code cosid.proxy.token}, timeouts) against a
 * real proxy server.
 */
@EnabledIf("me.ahoo.cosid.proxy.server.contract.ProxyEndToEnd#isAvailable")
@SpringBootTest(classes = ProxyServer.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StarterProxyClientEndToEndTest {
    @LocalServerPort
    int port;
    
    @DynamicPropertySource
    static void serverProperties(DynamicPropertyRegistry registry) {
        ProxyEndToEnd.serverProperties(registry);
    }
    
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({CosIdProperties.class, MachineProperties.class})
    static class ClientProperties {
    }
    
    private ApplicationContextRunner client(String token) {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class,
                CosIdProxyMachineIdDistributorAutoConfiguration.class, CosIdProxySegmentAutoConfiguration.class))
            .withUserConfiguration(ClientProperties.class)
            .withBean(MachineStateStorage.class, () -> MachineStateStorage.IN_MEMORY)
            .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT)
            .withPropertyValues(
                "coapi.mode=SYNC",
                "cosid.proxy.host=http://localhost:" + port,
                "cosid.proxy.token=" + token,
                "cosid.proxy.read-timeout=5s",
                "cosid.machine.enabled=true",
                "cosid.machine.distributor.type=proxy",
                "cosid.segment.enabled=true",
                "cosid.segment.distributor.type=proxy");
    }
    
    @Test
    void starterClientsDistributeWithToken() {
        client(ProxyEndToEnd.TOKEN).run(context -> {
            assertThat(context).hasNotFailed();
            String namespace = MockIdGenerator.usePrefix("starter").generateAsString();
            
            int machineId = context.getBean(ProxyMachineIdDistributor.class)
                .distribute(namespace, 4, InstanceId.of("starter", false), Duration.ofMinutes(1)).getMachineId();
            long maxId = context.getBean(IdSegmentDistributorFactory.class)
                .create(new IdSegmentDistributorDefinition(namespace, "starter", 0, 10)).nextMaxId();
            
            assertThat(machineId).isZero();
            assertThat(maxId).isEqualTo(10);
        });
    }
    
    @Test
    void starterClientsWithWrongTokenGetUnauthorized() {
        client("wrong-token").run(context -> {
            assertThat(context).hasNotFailed();
            assertThatThrownBy(() -> context.getBean(ProxyMachineIdDistributor.class)
                .distribute("starter-wrong", 4, InstanceId.of("starter", false), Duration.ofMinutes(1)))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        });
    }
}
