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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

class ProxyAuthWebFilterTest {
    
    @RestController
    static class Endpoints {
        @GetMapping({"/ids", "/ids/"})
        String id() {
            return "1";
        }
        
        @GetMapping("/machines/ns")
        String machine() {
            return "machine";
        }
        
        @GetMapping({"/actuator/health", "/actuator/health/**"})
        String health() {
            return "UP";
        }
        
        @GetMapping("/actuator/cosidGenerator")
        String generator() {
            return "generated";
        }
        
        @GetMapping("/v3/api-docs")
        String apiDocs() {
            return "docs";
        }
    }
    
    private static WebTestClient client(List<String> tokens) {
        return WebTestClient.bindToController(new Endpoints()).webFilter(new ProxyAuthWebFilter(tokens)).build();
    }
    
    /**
     * Raw URIs: WebTestClient must not normalize or re-encode them.
     */
    private static WebTestClient.ResponseSpec getRaw(WebTestClient client, String path) {
        return client.get().uri(URI.create(path)).exchange();
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
        "/ids", "/ids/", "/machines/ns", "/actuator/cosidGenerator", "/v3/api-docs",
        // WebFlux routes on the decoded path without matrix parameters: these must not dodge the filter.
        "/%6Dachines/ns", "/machines;x=1/ns", "/ids;a=b", "/%69ds", "/actuator/cosid%47enerator"
    })
    void rejectsEveryNonPublicPathWithoutToken(String path) {
        getRaw(client(List.of("secret")), path)
            .expectStatus().isUnauthorized()
            .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    }
    
    @Test
    void rejectsWrongTokenOrScheme() {
        WebTestClient client = client(List.of("secret"));
        client.get().uri("/machines/ns").header(HttpHeaders.AUTHORIZATION, "Bearer wrong").exchange().expectStatus().isUnauthorized();
        client.get().uri("/machines/ns").header(HttpHeaders.AUTHORIZATION, "Basic secret").exchange().expectStatus().isUnauthorized();
        client.get().uri("/machines/ns").header(HttpHeaders.AUTHORIZATION, "Bearer secret2").exchange().expectStatus().isUnauthorized();
    }
    
    @Test
    void acceptsAnyConfiguredToken() {
        WebTestClient client = client(List.of("old", "new"));
        client.get().uri("/machines/ns").header(HttpHeaders.AUTHORIZATION, "Bearer new").exchange()
            .expectStatus().isOk().expectBody(String.class).isEqualTo("machine");
        client.get().uri("/actuator/cosidGenerator").header(HttpHeaders.AUTHORIZATION, "bearer old").exchange()
            .expectStatus().isOk();
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/", "/actuator/health/liveness", "/actuator/%68ealth"})
    void healthIsPublic(String path) {
        getRaw(client(List.of("secret")), path).expectStatus().isOk().expectBody(String.class).isEqualTo("UP");
    }
    
    @Test
    void publicPathCannotReachAnotherEndpoint() {
        getRaw(client(List.of("secret")), "/actuator/health/../cosidGenerator")
            .expectBody(String.class).value(body -> org.assertj.core.api.Assertions.assertThat(body).isNotEqualTo("generated"));
    }
    
    @Test
    void publicPathsAreConfigurable() {
        WebTestClient client = WebTestClient.bindToController(new Endpoints())
            .webFilter(new ProxyAuthWebFilter(List.of("secret"), List.of("/v3/api-docs/**")))
            .build();
        client.get().uri("/v3/api-docs").exchange().expectStatus().isOk();
        client.get().uri("/actuator/health").exchange().expectStatus().isUnauthorized();
    }
    
    @Test
    void isOpenWithoutTokens() {
        client(List.of()).get().uri("/ids").exchange().expectStatus().isOk();
        client(Arrays.asList(" ", null)).get().uri("/actuator/cosidGenerator").exchange().expectStatus().isOk();
    }
}
