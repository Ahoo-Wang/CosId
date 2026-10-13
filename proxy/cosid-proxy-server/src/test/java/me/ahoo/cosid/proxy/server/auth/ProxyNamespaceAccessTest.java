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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Tokens limited to namespaces, and the server's namespaces, on the v1 and v2 machine APIs and the segment API.
 */
class ProxyNamespaceAccessTest {
    
    @RestController
    static class Endpoints {
        @RequestMapping({
            "/machines/{namespace}", "/machines/{namespace}/**",
            "/segments/{namespace}/{name}", "/segments/distributor/{namespace}/{name}",
            "/ids", "/ids/{name}"
        })
        String any() {
            return "ok";
        }
    }
    
    private static final List<ProxyAuthProperties.Client> CLIENTS = List.of(
        new ProxyAuthProperties.Client("order-token", List.of("order")),
        new ProxyAuthProperties.Client("user-token", List.of("user", "user-audit")));
    
    private static WebTestClient client(List<String> serverNamespaces) {
        ProxyAuthWebFilter filter = new ProxyAuthWebFilter(List.of("admin-token"), CLIENTS, ProxyAuthProperties.DEFAULT_PUBLIC_PATHS, serverNamespaces);
        return WebTestClient.bindToController(new Endpoints()).webFilter(filter).build();
    }
    
    private static WebTestClient.ResponseSpec post(WebTestClient client, String token, String path) {
        return client.post().uri(URI.create(path)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
        "/machines/order", "/machines/order/revert",
        "/machines/v2/order/claims/new", "/machines/v2/order/renewals", "/machines/v2/order/releases",
        "/segments/order/biz", "/segments/distributor/order/biz",
        // Decoded the way WebFlux routes.
        "/machines/%6Frder", "/machines;x=1/order"
    })
    void aTokenMayUseItsNamespaces(String path) {
        post(client(List.of()), "order-token", path).expectStatus().isOk();
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
        "/machines/user", "/machines/user/revert",
        "/machines/v2/user/claims/new", "/machines/v2/user/renewals", "/machines/v2/user/releases",
        "/segments/user/biz", "/segments/distributor/user/biz",
        "/machines/%75ser", "/segments/%75ser/biz", "/machines;x=1/user"
    })
    void aTokenMayNotUseOtherNamespaces(String path) {
        post(client(List.of()), "order-token", path).expectStatus().isForbidden();
    }
    
    @Test
    void aTokenWithSeveralNamespacesMayUseEach() {
        WebTestClient client = client(List.of());
        post(client, "user-token", "/machines/v2/user/claims/new").expectStatus().isOk();
        post(client, "user-token", "/segments/user-audit/biz").expectStatus().isOk();
        post(client, "user-token", "/segments/order/biz").expectStatus().isForbidden();
    }
    
    @Test
    void pathsWithoutANamespaceNeedOnlyAToken() {
        WebTestClient client = client(List.of("user"));
        post(client, "order-token", "/machines/v2/time").expectStatus().isOk();
        post(client, "order-token", "/ids").expectStatus().isOk();
        post(client, "order-token", "/ids/biz").expectStatus().isOk();
    }
    
    @Test
    void tokensWithoutNamespacesMayUseEveryNamespace() {
        WebTestClient client = client(List.of());
        post(client, "admin-token", "/machines/order").expectStatus().isOk();
        post(client, "admin-token", "/machines/v2/user/claims/new").expectStatus().isOk();
        post(client, "admin-token", "/segments/distributor/anything/biz").expectStatus().isOk();
    }
    
    @Test
    void theServerNamespacesApplyToEveryToken() {
        WebTestClient client = client(List.of("order"));
        post(client, "order-token", "/machines/v2/order/claims/new").expectStatus().isOk();
        post(client, "admin-token", "/machines/v2/user/claims/new").expectStatus().isForbidden();
        post(client, "admin-token", "/segments/other/biz").expectStatus().isForbidden();
    }
    
    @Test
    void theServerNamespacesApplyWithoutAuthentication() {
        ProxyAuthWebFilter filter = new ProxyAuthWebFilter(List.of(), List.of(), ProxyAuthProperties.DEFAULT_PUBLIC_PATHS, List.of("order"));
        WebTestClient client = WebTestClient.bindToController(new Endpoints()).webFilter(filter).build();
        client.post().uri("/machines/order").exchange().expectStatus().isOk();
        client.post().uri("/machines/user").exchange().expectStatus().isForbidden();
    }
    
    @Test
    void aWrongTokenIsStillUnauthorized() {
        post(client(List.of()), "wrong", "/machines/order").expectStatus().isUnauthorized();
    }
}
