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

import me.ahoo.cosid.proxy.server.ProxyServer;
import me.ahoo.cosid.proxy.server.configuration.ProxyServerConfiguration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * With virtual threads (Java 21+), the API calls run on Spring Boot's virtual-thread executor: the bounded pool is not
 * created. The build runs the tests on Java 17, where the pool stays; run them on Java 21 to check the virtual side.
 */
@EnabledIf("me.ahoo.cosid.proxy.server.contract.ProxyEndToEnd#isAvailable")
@SpringBootTest(classes = ProxyServer.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProxyVirtualThreadsEndToEndTest {
    @LocalServerPort
    int port;
    @Autowired
    ApplicationContext context;
    
    @DynamicPropertySource
    static void serverProperties(DynamicPropertyRegistry registry) {
        ProxyEndToEnd.serverProperties(registry);
        registry.add("spring.threads.virtual.enabled", () -> "true");
    }
    
    @Test
    void apiCallsRunOnVirtualThreads() {
        assertThat(context.containsBean("applicationTaskExecutor")).isTrue();
        if (Runtime.version().feature() >= 21) {
            assertThat(context.getBeansOfType(ProxyServerConfiguration.ProxyBlockingExecution.class)).isEmpty();
        } else {
            // Before Java 21 Spring Boot ignores spring.threads.virtual.enabled: the platform pool stays.
            assertThat(context.getBeansOfType(ProxyServerConfiguration.ProxyBlockingExecution.class)).hasSize(1);
        }
        
        long maxId = ProxyEndToEnd.segmentClient(port).nextMaxId("virtual", "order", 10);
        
        assertThat(maxId).isEqualTo(10);
    }
}
