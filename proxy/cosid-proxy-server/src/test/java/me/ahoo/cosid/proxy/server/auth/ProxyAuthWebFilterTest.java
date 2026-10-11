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

package me.ahoo.cosid.proxy.server.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

class ProxyAuthWebFilterTest {
    
    @RestController
    static class Endpoints {
        @GetMapping("/ids")
        String id() {
            return "1";
        }
        
        @GetMapping("/machines/ns")
        String machine() {
            return "machine";
        }
        
        @GetMapping("/actuator/health")
        String health() {
            return "UP";
        }
        
        @GetMapping("/idsx")
        String notProtected() {
            return "open";
        }
    }
    
    private static WebTestClient client(List<String> tokens) {
        return WebTestClient.bindToController(new Endpoints()).webFilter(new ProxyAuthWebFilter(tokens)).build();
    }
    
    @Test
    void rejectsProxyApiWithoutToken() {
        client(List.of("secret")).get().uri("/ids").exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    }
    
    @Test
    void rejectsProxyApiWithWrongToken() {
        client(List.of("secret")).get().uri("/machines/ns").header(HttpHeaders.AUTHORIZATION, "Bearer wrong").exchange()
            .expectStatus().isUnauthorized();
        client(List.of("secret")).get().uri("/machines/ns").header(HttpHeaders.AUTHORIZATION, "Basic secret").exchange()
            .expectStatus().isUnauthorized();
    }
    
    @Test
    void acceptsAnyConfiguredToken() {
        WebTestClient client = client(List.of("old", "new"));
        client.get().uri("/machines/ns").header(HttpHeaders.AUTHORIZATION, "Bearer new").exchange()
            .expectStatus().isOk().expectBody(String.class).isEqualTo("machine");
        client.get().uri("/ids").header(HttpHeaders.AUTHORIZATION, "bearer old").exchange()
            .expectStatus().isOk();
    }
    
    @Test
    void leavesOtherPathsOpen() {
        WebTestClient client = client(List.of("secret"));
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
        client.get().uri("/idsx").exchange().expectStatus().isOk();
    }
    
    @Test
    void isOpenWithoutTokens() {
        client(List.of()).get().uri("/ids").exchange().expectStatus().isOk();
        client(java.util.Arrays.asList(" ", null)).get().uri("/ids").exchange().expectStatus().isOk();
    }
}
