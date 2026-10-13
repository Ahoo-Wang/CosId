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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * The API calls block on the stores; they must not take the event loop with them, which also answers health checks.
 */
@EnabledIf("me.ahoo.cosid.proxy.server.contract.ProxyEndToEnd#isAvailable")
@SpringBootTest(classes = ProxyServer.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProxyBlockingExecutionEndToEndTest {
    @LocalServerPort
    int port;
    
    @DynamicPropertySource
    static void serverProperties(DynamicPropertyRegistry registry) {
        ProxyEndToEnd.serverProperties(registry);
        // Each segment call hangs on the paused Redis for as long as the test needs.
        registry.add("cosid.segment.distributor.redis.timeout", () -> "10s");
    }
    
    @Test
    void healthAnswersWhileStoreCallsHang() {
        ProxyEndToEnd.segmentClient(port).createDistributor("paused", "order", 0, 1);
        try (PausedRedis ignored = new PausedRedis(Duration.ofSeconds(10))) {
            // More hanging calls than the event loop has threads.
            Flux<Integer> hanging = PausedRedis.sendSegmentRequests(port, 32);
            hanging.subscribe();
            
            Integer health = WebClient.create("http://localhost:" + port)
                .get().uri("/actuator/health")
                .exchangeToMono(response -> response.toBodilessEntity().map(entity -> entity.getStatusCode().value()))
                .block(Duration.ofSeconds(3));
            
            assertThat(health).isEqualTo(200);
        }
    }
}
