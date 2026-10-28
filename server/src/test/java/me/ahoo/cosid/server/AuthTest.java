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

package me.ahoo.cosid.server;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.store.remote.RemoteApi;
import me.ahoo.cosid.store.remote.RemoteStore;
import me.ahoo.cosid.store.remote.RemoteStoreException;
import me.ahoo.cosid.store.tck.InMemoryStore;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Bearer authentication (08-remote.md §3): 401 without a valid token, 403 outside a client's namespaces and key
 * prefixes, the public health path, {@code Cache-Control: no-store}, and the refusal to start without a token.
 */
class AuthTest {
    private static final String ORDERS = "orders-token-0123456789";
    private static final String PREFIXED = "prefixed-token-0123456789";
    private static final String STAR = "star-token-0123456789";
    private static TestServer server;

    @BeforeAll
    static void start() {
        server = TestServer.memory(new InMemoryStore(),
            "cosid.server.auth.tokens[0]=" + TestServer.TOKEN,
            "cosid.server.auth.clients[0].name=orders",
            "cosid.server.auth.clients[0].token=" + ORDERS,
            "cosid.server.auth.clients[0].namespaces[0]=orders",
            "cosid.server.auth.clients[1].token=" + PREFIXED,
            "cosid.server.auth.clients[1].key-prefixes[0]=shared/orders-",
            "cosid.server.auth.clients[2].token=" + STAR,
            "cosid.server.auth.clients[2].namespaces[0]=*");
    }

    @AfterAll
    static void stop() {
        server.close();
    }

    private static String reserve(String key) {
        return "{\"key\":\"" + key + "\",\"size\":10,\"initialOffset\":0}";
    }

    @Test
    void missingOrUnknownTokenIs401() {
        HttpResponse<String> anonymous = server.post(RemoteApi.RESERVE, reserve("orders/a"), null);
        assertEquals(401, anonymous.statusCode());
        assertThat(anonymous.body(), containsString("\"code\":\"" + RemoteApi.UNAUTHORIZED + "\""));
        assertEquals("Bearer", anonymous.headers().firstValue("WWW-Authenticate").orElseThrow());
        assertEquals(401, server.post(RemoteApi.RESERVE, reserve("orders/a"), "wrong").statusCode());
        assertEquals(401, server.get("/actuator/prometheus", null).statusCode(), "actuator endpoints other than health need a token");

        try (RemoteStore wrong = server.client("wrong")) {
            RemoteStoreException error = assertThrows(RemoteStoreException.class, () -> wrong.ranges().reserve("orders/a", 1, 0));
            assertEquals(401, error.status());
            assertEquals(RemoteApi.UNAUTHORIZED, error.code());
            assertFalse(error.transientFailure());
        }
    }

    @Test
    void healthIsPublicAndEveryResponseIsNoStore() {
        HttpResponse<String> health = server.get("/actuator/health", null);
        assertEquals(200, health.statusCode());
        assertEquals("no-store", health.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(200, server.get("/actuator/health/liveness", null).statusCode());
        HttpResponse<String> reserved = server.post(RemoteApi.RESERVE, reserve("any/a"), TestServer.TOKEN);
        assertEquals(200, reserved.statusCode());
        assertEquals("no-store", reserved.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("no-store", server.post(RemoteApi.RESERVE, reserve("any/a"), null).headers().firstValue("Cache-Control").orElseThrow());
    }

    @Test
    void publicPathsReplaceTheDefaultHealthPaths() {
        try (TestServer custom = TestServer.memory(new InMemoryStore(), "cosid.server.auth.tokens[0]=" + TestServer.TOKEN,
            "cosid.server.auth.public-paths[0]=/actuator/info")) {
            HttpResponse<String> info = custom.get("/actuator/info", null);
            assertEquals(200, info.statusCode(), "the configured path needs no token");
            assertEquals("no-store", info.headers().firstValue("Cache-Control").orElseThrow());
            HttpResponse<String> health = custom.get("/actuator/health", null);
            assertEquals(401, health.statusCode(), "the list replaces the default one: health needs a token now");
            assertEquals(401, custom.get("/actuator/health/liveness", null).statusCode());
            assertEquals(200, custom.get("/actuator/health", TestServer.TOKEN).statusCode());
            assertEquals(401, custom.post(RemoteApi.RESERVE, reserve("any/a"), null).statusCode(), "the API still needs a token");
        }
        assertEquals(401, server.get("/actuator/info", null).statusCode(), "info is not public by default");
    }

    /**
     * C-B1, R1-server-01: Actuator (the cosid endpoint issues ids) and springdoc ignore client scopes; a client token
     * gets the client paths only, so 403 there and anywhere else.
     */
    @Test
    void clientTokensGetTheClientPathsOnly() {
        List<String> adminPaths = List.of("/actuator", "/actuator/cosid", "/actuator/cosid/default", "/actuator/cosid/default/long",
            "/actuator/prometheus", "/actuator/info", "/v3/api-docs", "/v3/api-docs.yaml", "/swagger-ui.html", "/swagger-ui/index.html",
            "/%61ctuator/cosid", "/actuator;x=y/cosid", "/actuator/cosid/", "/anything-else", "/ranges");
        for (String client : List.of(ORDERS, PREFIXED, STAR)) {
            for (String path : adminPaths) {
                HttpResponse<String> response = server.get(path, client);
                assertEquals(403, response.statusCode(), () -> path + " with a client token: " + response.body());
                assertThat(response.body(), containsString("\"code\":\"" + RemoteApi.FORBIDDEN + "\""));
            }
            assertEquals(200, server.get("/actuator/health", client).statusCode(), "the public paths stay public");
        }
        for (String path : List.of("/actuator", "/actuator/cosid", "/actuator/prometheus", "/v3/api-docs", "/swagger-ui/index.html")) {
            assertEquals(200, server.get(path, TestServer.TOKEN).statusCode(), () -> path + " with an unrestricted token");
        }
        // Dot segments get our 400 (or 403); an encoded slash gets Tomcat's own 400 page before the filter. Either way: refused.
        for (String path : List.of("/ids/../actuator/cosid", "/ids/%2e%2e/actuator/cosid", "/ids/./x", "/ids%2F..%2Factuator/cosid")) {
            HttpResponse<String> response = server.send(HttpRequest.newBuilder(URI.create(server.baseUrl() + path)).header("Authorization", "Bearer " + ORDERS)
                .GET().build());
            assertTrue(response.statusCode() == 400 || response.statusCode() == 403, () -> path + " with a client token: " + response.statusCode());
        }
    }

    @Test
    void docsNeedATokenByDefault() {
        for (String path : List.of("/swagger-ui.html", "/swagger-ui/index.html", "/v3/api-docs", "/v3/api-docs/swagger-config", "/v3/api-docs.yaml")) {
            assertEquals(401, server.get(path, null).statusCode(), () -> path + " without a token");
        }
    }

    /** docs.public opens the read-only springdoc paths, on top of public-paths; the API and Actuator keep their rules. */
    @Test
    void docsPublicOpensTheDocumentationOnly() {
        try (TestServer docs = TestServer.memory(new InMemoryStore(), "cosid.server.auth.tokens[0]=" + TestServer.TOKEN,
            "cosid.server.auth.clients[0].token=" + ORDERS, "cosid.server.auth.clients[0].namespaces[0]=orders",
            "cosid.server.ids.enabled=true", "cosid.server.docs.public=true")) {
            HttpResponse<String> ui = docs.get("/swagger-ui.html", null);
            assertEquals(302, ui.statusCode(), "springdoc redirects to the UI");
            assertThat(ui.headers().firstValue("Location").orElseThrow(), containsString("/swagger-ui/index.html"));
            for (String path : List.of("/swagger-ui/index.html", "/swagger-ui/swagger-initializer.js", "/v3/api-docs", "/v3/api-docs/swagger-config",
                "/v3/api-docs.yaml")) {
                HttpResponse<String> response = docs.get(path, null);
                assertEquals(200, response.statusCode(), () -> path + " without a token: " + response.body());
            }
            assertThat(docs.get("/v3/api-docs", null).body(), containsString("\"bearer\""));
            assertEquals(200, docs.get("/actuator/health", null).statusCode(), "public-paths are kept, not replaced");
            assertEquals(401, docs.get("/ids", null).statusCode(), "the id endpoints still need a token");
            assertEquals(401, docs.post(RemoteApi.RESERVE, reserve("orders/a"), null).statusCode(), "the API still needs a token");
            assertEquals(401, docs.get("/actuator/cosid", null).statusCode(), "Actuator still needs a token");
            assertEquals(403, docs.get("/actuator/cosid", ORDERS).statusCode(), "Actuator still needs an unrestricted token");
            assertEquals(403, docs.get("/actuator/prometheus", ORDERS).statusCode());
            assertEquals(200, docs.get("/actuator/cosid", TestServer.TOKEN).statusCode());
        }
    }

    /** R1-server-01: moving Actuator or springdoc does not open them to client tokens. */
    @Test
    void movedActuatorAndDocsStayClosedToClients() {
        try (TestServer moved = TestServer.memory(new InMemoryStore(), "cosid.server.auth.tokens[0]=" + TestServer.TOKEN,
            "cosid.server.auth.clients[0].token=" + ORDERS, "cosid.server.auth.clients[0].namespaces[0]=orders",
            "management.endpoints.web.base-path=/manage", "springdoc.api-docs.path=/openapi")) {
            for (String path : List.of("/manage/cosid", "/manage/cosid/default", "/manage/prometheus", "/openapi", "/actuator/cosid")) {
                HttpResponse<String> response = moved.get(path, ORDERS);
                assertEquals(403, response.statusCode(), () -> path + " with a client token: " + response.body());
            }
            assertEquals(200, moved.get("/manage/cosid", TestServer.TOKEN).statusCode());
            assertEquals(200, moved.get("/openapi", TestServer.TOKEN).statusCode());
            assertEquals(200, moved.post(RemoteApi.RESERVE, reserve("orders/" + UUID.randomUUID()), ORDERS).statusCode());
        }
    }

    /** Under spring.mvc.servlet.path every MVC path moves: the client paths and docs.public follow it. */
    @Test
    void clientPathsAndDocsFollowTheServletPath() {
        try (TestServer api = TestServer.memory(new InMemoryStore(), "cosid.server.auth.tokens[0]=" + TestServer.TOKEN,
            "cosid.server.auth.clients[0].token=" + ORDERS, "cosid.server.auth.clients[0].namespaces[0]=orders",
            "spring.mvc.servlet.path=/api", "cosid.server.docs.public=true")) {
            assertEquals(200, api.post("/api" + RemoteApi.RESERVE, reserve("orders/" + UUID.randomUUID()), ORDERS).statusCode());
            assertEquals(403, api.get("/api/actuator/cosid", ORDERS).statusCode());
            assertEquals(200, api.get("/api/actuator/cosid", TestServer.TOKEN).statusCode());
            assertEquals(200, api.get("/api/v3/api-docs", null).statusCode(), "docs.public follows the servlet path");
            assertEquals(401, api.get("/api/actuator/cosid", null).statusCode());
        }
    }

    /** R1-server-01: Actuator on a port of its own is outside this filter, so the server refuses it. */
    @Test
    void refusesASeparateManagementPort() {
        Exception error = assertThrows(Exception.class, () -> TestServer.memory(new InMemoryStore(), "management.server.port=0"));
        assertTrue(rootMessage(error).contains("refuses management.server.port"), () -> rootMessage(error));
        Grants tokens = Grants.of(new ServerProperties.Auth(false, List.of(TestServer.TOKEN), List.of(), List.of()));
        CosIdServer.requireOneManagementPort(environment("server.port", "8688", "management.server.port", "8688"), tokens);
        CosIdServer.requireOneManagementPort(environment("management.server.port", "8080"), tokens);
        CosIdServer.requireOneManagementPort(environment("management.server.port", "-1"), tokens);
        assertThrows(IllegalStateException.class, () -> CosIdServer.requireOneManagementPort(environment("management.server.port", "9000"), tokens));
        CosIdServer.requireOneManagementPort(environment("management.server.port", "9000"),
            Grants.of(new ServerProperties.Auth(true, List.of(), List.of(), List.of())));
    }

    @Test
    void clientIsLimitedToItsNamespaces() {
        try (RemoteStore orders = server.client(ORDERS)) {
            assertEquals(new Range(0, 5), orders.ranges().reserve("orders/" + UUID.randomUUID(), 5, 0));
            RemoteStoreException range = assertThrows(RemoteStoreException.class, () -> orders.ranges().reserve("billing/a", 5, 0));
            assertEquals(403, range.status());
            assertEquals(RemoteApi.FORBIDDEN, range.code());
            assertEquals(403, assertThrows(RemoteStoreException.class, () -> orders.ranges().seal("billing/a")).status());

            Owner owner = new Owner("host", UUID.randomUUID());
            assertInstanceOf(AcquireResult.Acquired.class,
                orders.slots().acquire("orders", owner, 4, Duration.ofSeconds(10), Duration.ofSeconds(1), 0, OptionalInt.empty()));
            RemoteStoreException slots = assertThrows(RemoteStoreException.class,
                () -> orders.slots().acquire("billing", owner, 4, Duration.ofSeconds(10), Duration.ofSeconds(1), 0, OptionalInt.empty()));
            assertEquals(403, slots.status());
        }
    }

    @Test
    void clientIsLimitedToItsKeyPrefixes() {
        try (RemoteStore prefixed = server.client(PREFIXED)) {
            assertEquals(new Range(0, 5), prefixed.ranges().reserve("shared/orders-" + UUID.randomUUID(), 5, 0));
            assertEquals(403, assertThrows(RemoteStoreException.class, () -> prefixed.ranges().reserve("shared/billing-a", 5, 0)).status());
            assertEquals(403, assertThrows(RemoteStoreException.class, () -> prefixed.slots().acquire("shared", new Owner("host", UUID.randomUUID()), 4,
                Duration.ofSeconds(10), Duration.ofSeconds(1), 0, OptionalInt.empty())).status(), "key prefixes do not open slot namespaces");
        }
    }

    @Test
    void starAllowsEveryNamespace() {
        try (RemoteStore star = server.client(STAR)) {
            assertEquals(new Range(0, 5), star.ranges().reserve("anything/" + UUID.randomUUID(), 5, 0));
        }
    }

    @Test
    void refusesToStartWithoutAToken() {
        Exception error = assertThrows(Exception.class, () -> TestServer.memory(new InMemoryStore(), "cosid.server.auth.allow-anonymous=false"));
        assertTrue(rootMessage(error).contains("refuses to start without authentication"), () -> rootMessage(error));
    }

    @Test
    void refusesAClientWithoutScope() {
        Exception error = assertThrows(Exception.class, () -> TestServer.memory(new InMemoryStore(), "cosid.server.auth.clients[0].token=" + ORDERS));
        assertTrue(rootMessage(error).contains("give it namespaces or key-prefixes"), () -> rootMessage(error));
    }

    @Test
    void refusesBlankAndRepeatedTokens() {
        Exception blank = assertThrows(Exception.class, () -> TestServer.memory(new InMemoryStore(), "cosid.server.auth.tokens[0]= "));
        assertTrue(rootMessage(blank).contains("is blank"), () -> rootMessage(blank));
        Exception repeated = assertThrows(Exception.class, () -> TestServer.memory(new InMemoryStore(),
            "cosid.server.auth.tokens[0]=" + STAR, "cosid.server.auth.clients[0].token=" + STAR, "cosid.server.auth.clients[0].namespaces[0]=x"));
        assertTrue(rootMessage(repeated).contains("repeats another token"), () -> rootMessage(repeated));
    }

    /** C-B2: a placeholder nobody resolved must not become the token the documentation shows. */
    @Test
    void refusesUnresolvedPlaceholderTokens() {
        Grants.of(new ServerProperties.Auth(false, List.of(TestServer.TOKEN), List.of(), List.of()));
        for (String token : List.of("${COSID_SERVER_TOKEN}", "prefix-${COSID_SERVER_TOKEN}-0123456789")) {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> Grants.of(new ServerProperties.Auth(false, List.of(token), List.of(), List.of())));
            assertThat(error.getMessage(), containsString("unresolved placeholder"));
            IllegalStateException client = assertThrows(IllegalStateException.class, () -> Grants.of(new ServerProperties.Auth(false, List.of(),
                List.of(new ServerProperties.Client("orders", token, List.of("orders"), List.of())), List.of())));
            assertThat(client.getMessage(), containsString("clients[0].token"));
        }
        Exception started = assertThrows(Exception.class, () -> TestServer.memory(new InMemoryStore(),
            "cosid.server.auth.tokens[0]=${COSID_SERVER_TOKEN_THAT_IS_NOT_SET}"));
        // The binder ignores placeholders it cannot resolve: the literal text arrives, as with a documented
        // tokens: [ "${COSID_SERVER_TOKEN}" ] and no such variable.
        assertTrue(rootMessage(started).contains("unresolved placeholder"), () -> rootMessage(started));
    }

    @Test
    void refusesShortTokens() {
        String fifteen = "a".repeat(Grants.MIN_TOKEN_LENGTH - 1);
        Exception error = assertThrows(Exception.class, () -> TestServer.memory(new InMemoryStore(), "cosid.server.auth.tokens[0]=" + fifteen));
        assertTrue(rootMessage(error).contains("shorter than 16 characters"), () -> rootMessage(error));
        assertThrows(IllegalStateException.class, () -> Grants.of(new ServerProperties.Auth(false, List.of(),
            List.of(new ServerProperties.Client("short", " " + fifteen + " ", List.of("x"), List.of())), List.of())));
        Grants.of(new ServerProperties.Auth(false, List.of("a".repeat(Grants.MIN_TOKEN_LENGTH)), List.of(), List.of()));
    }

    @Test
    void allowAnonymousStartsOpen() {
        try (TestServer open = TestServer.memory(new InMemoryStore(), "cosid.server.auth.allow-anonymous=true");
             RemoteStore anonymous = open.client(null);
             RemoteStore tokened = open.client("whatever")) {
            assertEquals(new Range(0, 5), anonymous.ranges().reserve("open/a", 5, 0));
            assertEquals(new Range(5, 10), tokened.ranges().reserve("open/a", 5, 0), "an open server ignores tokens");
        }
    }

    private static StandardEnvironment environment(String... keysAndValues) {
        Map<String, Object> properties = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            properties.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return environment;
    }

    static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return String.valueOf(cause.getMessage());
    }
}
