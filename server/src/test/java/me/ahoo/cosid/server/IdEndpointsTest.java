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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.store.remote.RemoteApi;
import me.ahoo.cosid.store.tck.InMemoryStore;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;

/**
 * The optional id endpoints (08-remote.md §2), served by the generators of the server's runtime: {@code default}
 * without a name, {@code strings} through the codec, {@code periodic} values of one period, {@code count}, and the
 * namespace check of client tokens.
 */
class IdEndpointsTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SERVICE = "service-token";
    private static final String OTHER = "other-token";
    private static TestServer server;

    @BeforeAll
    static void start() {
        server = TestServer.memory(new InMemoryStore(),
            "cosid.server.ids.enabled=true",
            "cosid.server.limits.max-count=50",
            "cosid.server.auth.tokens[0]=" + TestServer.TOKEN,
            "cosid.server.auth.clients[0].token=" + SERVICE,
            "cosid.server.auth.clients[0].namespaces[0]=ids-test",
            "cosid.server.auth.clients[1].token=" + OTHER,
            "cosid.server.auth.clients[1].namespaces[0]=elsewhere",
            "cosid.namespace=ids-test",
            "cosid.machine.leased.store=mem",
            "cosid.generators.default.strategy=snowflake",
            "cosid.generators.order.strategy=segment",
            "cosid.generators.order.store=mem",
            "cosid.generators.order.codec[0].radix.radix=62",
            "cosid.generators.order.codec[1].prefix=O",
            "cosid.generators.bill.strategy=periodic-segment",
            "cosid.generators.bill.store=mem",
            "cosid.generators.bill.period=YEAR_MONTH_DAY",
            "cosid.generators.off.strategy=segment",
            "cosid.generators.off.store=mem",
            "cosid.generators.off.disabled=true");
    }

    @AfterAll
    static void stop() {
        server.close();
    }

    private static JsonNode ok(String path, String token) {
        HttpResponse<String> response = server.get(path, token);
        assertEquals(200, response.statusCode(), () -> path + " -> " + response.body());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        return JSON.readTree(response.body());
    }

    private static void assertError(String path, int status, String code) {
        HttpResponse<String> response = server.get(path, TestServer.TOKEN);
        assertEquals(status, response.statusCode(), () -> path + " -> " + response.body());
        assertThat(response.body(), containsString("\"code\":\"" + code + "\""));
    }

    @Test
    void defaultGeneratorWithoutAName() {
        JsonNode one = ok(RemoteApi.IDS, TestServer.TOKEN);
        assertEquals(1, one.get("ids").size());
        JsonNode ids = ok(RemoteApi.IDS + "?count=20", TestServer.TOKEN).get("ids");
        Set<Long> distinct = new HashSet<>();
        ids.forEach(id -> distinct.add(id.asLong()));
        assertEquals(20, distinct.size());
        assertEquals(3, ok(RemoteApi.IDS + "/strings?count=3", TestServer.TOKEN).get("ids").size());
        assertEquals(2, ok(RemoteApi.IDS + "/default?count=2", TestServer.TOKEN).get("ids").size());
    }

    @Test
    void namedGeneratorAndItsCodec() {
        JsonNode ids = ok(RemoteApi.IDS + "/order?count=5", TestServer.TOKEN).get("ids");
        assertEquals(5, ids.size());
        for (int i = 1; i < ids.size(); i++) {
            assertThat(ids.get(i).asLong(), greaterThan(ids.get(i - 1).asLong()));
        }
        JsonNode strings = ok(RemoteApi.IDS + "/order/strings?count=2", TestServer.TOKEN).get("ids");
        assertEquals(2, strings.size());
        assertThat(strings.get(0).asString(), startsWith("O"));
    }

    @Test
    void periodicValuesShareAPeriod() {
        JsonNode body = ok(RemoteApi.IDS + "/bill/periodic?count=4", TestServer.TOKEN);
        assertEquals(8, body.get("period").asString().length());
        assertEquals(4, body.get("values").size());
        assertThat(body.get("values").get(3).asLong(), greaterThan(body.get("values").get(0).asLong()));
    }

    /** D30: the API has no version prefix; with every endpoint on, it shadows none of Actuator or springdoc. */
    @Test
    void unversionedPathsLeaveActuatorAndSpringdocAlone() {
        HttpResponse<String> api = server.get("/v3/api-docs", TestServer.TOKEN);
        assertEquals(200, api.statusCode());
        for (String path : new String[] {RemoteApi.RESERVE, RemoteApi.SEAL, RemoteApi.ACQUIRE, RemoteApi.RENEW, RemoteApi.RELEASE, RemoteApi.IDS,
            RemoteApi.IDS + "/strings", RemoteApi.IDS + "/{generator}", RemoteApi.IDS + "/{generator}/strings", RemoteApi.IDS + "/{generator}/periodic"}) {
            assertThat(api.body(), containsString("\"" + path + "\""));
        }
        assertThat(api.body(), not(containsString("/v5")));
        assertEquals("/ranges:reserve", RemoteApi.RESERVE);
        assertEquals("/ids", RemoteApi.IDS);
        assertEquals(200, server.get("/swagger-ui/index.html", TestServer.TOKEN).statusCode());
        assertEquals(200, server.get("/actuator/health", null).statusCode());
        assertEquals(200, server.get("/actuator/cosid", TestServer.TOKEN).statusCode());
        assertEquals(200, server.get("/actuator/prometheus", TestServer.TOKEN).statusCode());
        assertEquals(404, server.get("/v5/ids", TestServer.TOKEN).statusCode(), "the old prefix is gone");
    }

    @Test
    void errors() {
        assertError(RemoteApi.IDS + "/missing", 404, RemoteApi.NOT_FOUND);
        assertError(RemoteApi.IDS + "/order/periodic", 404, RemoteApi.NOT_FOUND);
        assertError(RemoteApi.IDS + "/off", 409, RemoteApi.GENERATOR_DISABLED);
        assertError(RemoteApi.IDS + "?count=0", 400, RemoteApi.BAD_REQUEST);
        assertError(RemoteApi.IDS + "?count=51", 400, RemoteApi.BAD_REQUEST);
        assertError(RemoteApi.IDS + "?count=x", 400, RemoteApi.BAD_REQUEST);
    }

    @Test
    void clientTokensNeedTheRuntimeNamespace() {
        assertTrue(ok(RemoteApi.IDS + "/order", SERVICE).get("ids").get(0).asLong() > 0);
        assertEquals(403, server.get(RemoteApi.IDS + "/order", OTHER).statusCode());
        assertEquals(403, server.get(RemoteApi.IDS + "/bill/periodic", OTHER).statusCode());
        assertEquals(401, server.get(RemoteApi.IDS, null).statusCode());
    }
}
