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

import me.ahoo.cosid.proxy.api.ErrorResponse;
import me.ahoo.cosid.proxy.server.ProxyServer;
import me.ahoo.cosid.proxy.server.configuration.ProxyServerConfiguration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

/**
 * Beyond its API thread pool, the server answers 503 at once instead of queueing requests behind a hung store.
 */
@EnabledIf("me.ahoo.cosid.proxy.server.contract.ProxyEndToEnd#isAvailable")
@SpringBootTest(classes = ProxyServer.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProxyOverloadEndToEndTest {
    @LocalServerPort
    int port;
    @Autowired
    ProxyServerConfiguration.ProxyBlockingExecution cosIdProxyBlockingExecution;
    
    @DynamicPropertySource
    static void serverProperties(DynamicPropertyRegistry registry) {
        ProxyEndToEnd.serverProperties(registry);
        registry.add("cosid.segment.distributor.redis.timeout", () -> "10s");
        registry.add("cosid.proxy.server.blocking.threads", () -> "2");
        registry.add("cosid.proxy.server.blocking.queue-capacity", () -> "2");
    }
    
    @Test
    void answers503BeyondTheThreadPool() throws InterruptedException {
        ProxyEndToEnd.segmentClient(port).createDistributor("paused", "order", 0, 1);
        try (PausedRedis ignored = new PausedRedis(Duration.ofSeconds(10))) {
            PausedRedis.sendSegmentRequests(port, 2).subscribe();
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (cosIdProxyBlockingExecution.getActiveCount() < 2 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertThat(cosIdProxyBlockingExecution.getActiveCount()).isEqualTo(2);
            
            // Both threads hang on Redis: further requests fill the queue, and the ones beyond it are rejected at once.
            WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + ProxyEndToEnd.TOKEN)
                .build();
            ResponseEntity<ErrorResponse> rejected = Flux.range(0, 6)
                .flatMap(i -> webClient.patch().uri("/segments/paused/order?step=1")
                    .exchangeToMono(response -> response.toEntity(ErrorResponse.class)), 6)
                .filter(response -> response.getStatusCode().value() != 200)
                .next()
                .block(Duration.ofSeconds(3));
            
            assertThat(rejected).isNotNull();
            assertThat(rejected.getStatusCode().value()).isEqualTo(503);
            assertThat(rejected.getHeaders().get(HttpHeaders.RETRY_AFTER)).isEqualTo(List.of("1"));
            assertThat(rejected.getBody()).isNotNull();
            assertThat(rejected.getBody().getCode()).isEqualTo(ErrorResponse.SERVICE_UNAVAILABLE);
        }
    }
}
