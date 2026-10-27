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

package me.ahoo.cosid.example.snowflake;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Smoke test: the application leases a slot from Redis ({@code COSID_TEST_REDIS_URI}, or a Redis in Testcontainers)
 * and hands out Snowflake ids, trace ids and 80-bit tokens; the {@code cosid} health indicator reports the lease.
 */
class SnowflakeRedisApplicationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static GenericContainer<?> redis;
    private static ConfigurableApplicationContext context;
    private static String baseUrl;

    @BeforeAll
    static void start() {
        String uri = System.getenv("COSID_TEST_REDIS_URI");
        if (uri == null || uri.isBlank()) {
            redis = new GenericContainer<>(DockerImageName.parse("redis:8.10-alpine")).withExposedPorts(6379);
            redis.start();
            uri = "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379);
        }
        context = SpringApplication.run(SnowflakeRedisApplication.class, "--server.port=0", "--REDIS_URI=" + uri);
        baseUrl = "http://localhost:" + context.getEnvironment().getProperty("local.server.port");
    }

    @AfterAll
    static void stop() {
        if (context != null) {
            context.close();
        }
        if (redis != null) {
            redis.stop();
        }
    }

    private static JsonNode get(String path) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response::body);
        return JSON.readTree(response.body());
    }

    @Test
    void idsIncreaseOnTheLeasedSlot() throws Exception {
        JsonNode first = get("/ids/next");
        JsonNode second = get("/ids/next");
        assertThat(second.get("id").asLong(), greaterThan(first.get("id").asLong()));
        assertEquals(11, first.get("text").asString().length());
        assertThat(first.get("slot").asInt(), lessThan(1024));
        Instant issuedAt = Instant.parse(first.get("issuedAt").asString());
        assertThat(Duration.between(issuedAt, Instant.now()).abs().toMinutes(), lessThan(1L));
    }

    @Test
    void traceAndTokenIdsAreText() throws Exception {
        assertNotEquals(get("/traces/next").get("text").asString(), get("/traces/next").get("text").asString());
        assertNotEquals(get("/tokens/next").get("text").asString(), get("/tokens/next").get("text").asString());
    }

    @Test
    void healthReportsTheLease() throws Exception {
        JsonNode health = get("/actuator/health");
        assertEquals("UP", health.path("status").asString(), health::toString);
        JsonNode cosid = health.at("/components/cosId");
        assertEquals("UP", cosid.path("status").asString(), health::toString);
        assertThat(get("/actuator/cosid").size(), greaterThanOrEqualTo(3));
    }
}
