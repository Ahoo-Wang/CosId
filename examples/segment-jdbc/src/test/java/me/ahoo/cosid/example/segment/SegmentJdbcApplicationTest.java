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

package me.ahoo.cosid.example.segment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Smoke test: the application starts on H2 and hands out increasing order ids, today's bill numbers and filled entities.
 */
class SegmentJdbcApplicationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static ConfigurableApplicationContext context;
    private static String baseUrl;

    @BeforeAll
    static void start() {
        context = SpringApplication.run(SegmentJdbcApplication.class, "--server.port=0");
        baseUrl = "http://localhost:" + context.getEnvironment().getProperty("local.server.port");
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    private static JsonNode call(HttpRequest.Builder request) throws Exception {
        HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response::body);
        return JSON.readTree(response.body());
    }

    private static JsonNode get(String path) throws Exception {
        return call(HttpRequest.newBuilder(URI.create(baseUrl + path)));
    }

    @Test
    void orderIdsIncreaseAndCarryTheCodec() throws Exception {
        JsonNode first = get("/orders/next");
        JsonNode second = get("/orders/next");
        assertThat(second.get("id").asLong(), greaterThan(first.get("id").asLong()));
        assertThat(first.get("text").asString(), startsWith("O"));
    }

    @Test
    void billNumbersBelongToToday() throws Exception {
        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.BASIC_ISO_DATE);
        JsonNode bill = get("/bills/next");
        assertEquals(today, bill.get("period").asString());
        assertThat(bill.get("text").asString(), matchesPattern(today + "-\\d{6}"));
    }

    @Test
    void newOrdersGetAnId() throws Exception {
        JsonNode order = call(HttpRequest.newBuilder(URI.create(baseUrl + "/orders"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"item\":\"book\"}")));
        assertThat(order.get("id").asLong(), greaterThan(0L));
        assertEquals("book", order.get("item").asString());
    }
}
