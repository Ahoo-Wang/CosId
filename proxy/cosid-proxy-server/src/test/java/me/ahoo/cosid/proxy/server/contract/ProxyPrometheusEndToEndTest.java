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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Prometheus scrapes the server's metrics with the token; without it the endpoint is closed like the API.
 */
@EnabledIf("me.ahoo.cosid.proxy.server.contract.ProxyEndToEnd#isAvailable")
@SpringBootTest(classes = ProxyServer.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProxyPrometheusEndToEndTest {
    @LocalServerPort
    int port;
    
    @DynamicPropertySource
    static void serverProperties(DynamicPropertyRegistry registry) {
        ProxyEndToEnd.serverProperties(registry);
    }
    
    private ResponseEntity<String> scrape(String token) {
        WebClient.RequestHeadersSpec<?> request = WebClient.create("http://localhost:" + port).get().uri("/actuator/prometheus");
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request.exchangeToMono(response -> response.toEntity(String.class)).block(Duration.ofSeconds(10));
    }
    
    @Test
    void scrapesTheMetricsWithTheToken() {
        ProxyEndToEnd.segmentClient(port).nextMaxId("prometheus", "order", 10);
        
        ResponseEntity<String> scraped = scrape(ProxyEndToEnd.TOKEN);
        
        assertThat(scraped).isNotNull();
        assertThat(scraped.getStatusCode().value()).isEqualTo(200);
        assertThat(scraped.getBody())
            .contains("jvm_memory_used_bytes")
            .contains("http_server_requests_seconds_count")
            .contains("uri=\"/segments/{namespace}/{name}\"")
            // The CosId meters of the server's own generators and machine id lease.
            .contains("cosid_segment_remaining")
            .contains("cosid_machine_lease_remaining_seconds")
            .contains("cosid_sequence_exhausted_wait_seconds_count");
    }
    
    @Test
    void needsTheToken() {
        ResponseEntity<String> anonymous = scrape(null);
        
        assertThat(anonymous).isNotNull();
        assertThat(anonymous.getStatusCode().value()).isEqualTo(401);
    }
}
