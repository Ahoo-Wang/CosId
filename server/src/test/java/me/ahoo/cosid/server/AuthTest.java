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

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Bearer authentication (08-remote.md §3): 401 without a valid token, 403 outside a client's namespaces and key
 * prefixes, the public health path, {@code Cache-Control: no-store}, and the refusal to start without a token.
 */
class AuthTest {
    private static final String ORDERS = "orders-token";
    private static final String PREFIXED = "prefixed-token";
    private static final String STAR = "star-token";
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
                orders.slots().acquire("orders", owner, 4, Duration.ofSeconds(10), Duration.ofSeconds(1), OptionalInt.empty()));
            RemoteStoreException slots = assertThrows(RemoteStoreException.class,
                () -> orders.slots().acquire("billing", owner, 4, Duration.ofSeconds(10), Duration.ofSeconds(1), OptionalInt.empty()));
            assertEquals(403, slots.status());
        }
    }

    @Test
    void clientIsLimitedToItsKeyPrefixes() {
        try (RemoteStore prefixed = server.client(PREFIXED)) {
            assertEquals(new Range(0, 5), prefixed.ranges().reserve("shared/orders-" + UUID.randomUUID(), 5, 0));
            assertEquals(403, assertThrows(RemoteStoreException.class, () -> prefixed.ranges().reserve("shared/billing-a", 5, 0)).status());
            assertEquals(403, assertThrows(RemoteStoreException.class, () -> prefixed.slots().acquire("shared", new Owner("host", UUID.randomUUID()), 4,
                Duration.ofSeconds(10), Duration.ofSeconds(1), OptionalInt.empty())).status(), "key prefixes do not open slot namespaces");
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
        Exception error = assertThrows(Exception.class, () -> TestServer.memory(new InMemoryStore(), "cosid.server.auth.clients[0].token=x"));
        assertTrue(rootMessage(error).contains("give it namespaces or key-prefixes"), () -> rootMessage(error));
    }

    @Test
    void refusesBlankAndRepeatedTokens() {
        Exception blank = assertThrows(Exception.class, () -> TestServer.memory(new InMemoryStore(), "cosid.server.auth.tokens[0]= "));
        assertTrue(rootMessage(blank).contains("is blank"), () -> rootMessage(blank));
        Exception repeated = assertThrows(Exception.class, () -> TestServer.memory(new InMemoryStore(),
            "cosid.server.auth.tokens[0]=a", "cosid.server.auth.clients[0].token=a", "cosid.server.auth.clients[0].namespaces[0]=x"));
        assertTrue(rootMessage(repeated).contains("repeats another token"), () -> rootMessage(repeated));
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

    static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return String.valueOf(cause.getMessage());
    }
}
