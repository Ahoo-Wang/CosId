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

package me.ahoo.cosid.example.server;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Smoke test: cosid-server runs as its own process, the built jar, on a ZooKeeper started here; the client application
 * leases its slot and reserves its segments through the server, and the server's optional id endpoint answers a token.
 */
class ServerClientApplicationTest {
    private static final String TOKEN = "example-token";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static TestingServer zookeeper;
    private static Process server;
    private static String serverUrl;
    private static ConfigurableApplicationContext client;
    private static String clientUrl;

    @BeforeAll
    static void start() throws Exception {
        zookeeper = new TestingServer(true);
        int port = freePort();
        serverUrl = "http://localhost:" + port;
        Path log = Path.of(System.getProperty("java.io.tmpdir"), "cosid-example-server-" + port + ".log");
        server = new ProcessBuilder(List.of(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-jar", System.getProperty("cosid.example.server.jar"),
            "--server.port=" + port,
            "--cosid.namespace=example",
            "--cosid.stores.main.type=zookeeper",
            "--cosid.stores.main.options.connectString=" + zookeeper.getConnectString(),
            "--cosid.server.auth.tokens[0]=" + TOKEN,
            "--cosid.server.ids.enabled=true",
            "--cosid.generators.ticket.strategy=segment",
            "--cosid.generators.ticket.store=main"))
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start();
        awaitLiveness(log.toFile());
        client = SpringApplication.run(ServerClientApplication.class, "--server.port=0",
            "--COSID_SERVER_URL=" + serverUrl, "--COSID_SERVER_TOKEN=" + TOKEN);
        clientUrl = "http://localhost:" + client.getEnvironment().getProperty("local.server.port");
    }

    @AfterAll
    static void stop() throws Exception {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.destroy();
            if (!server.waitFor(30, TimeUnit.SECONDS)) {
                server.destroyForcibly();
            }
        }
        if (zookeeper != null) {
            zookeeper.close();
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void awaitLiveness(File log) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        while (System.nanoTime() < deadline) {
            if (!server.isAlive()) {
                throw new IllegalStateException("cosid-server exited, see " + log);
            }
            try {
                if (send(serverUrl + "/actuator/health/liveness", null).statusCode() == 200) {
                    return;
                }
            } catch (ConnectException notYet) {
                // still starting
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("cosid-server did not start in time, see " + log);
    }

    private static HttpResponse<String> send(String url, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode ok(String url, String token) throws Exception {
        HttpResponse<String> response = send(url, token);
        assertEquals(200, response.statusCode(), response::body);
        return JSON.readTree(response.body());
    }

    @Test
    void theClientIssuesSnowflakeIdsOnASlotLeasedThroughTheServer() throws Exception {
        JsonNode first = ok(clientUrl + "/ids/next", null);
        JsonNode second = ok(clientUrl + "/ids/next", null);
        assertThat(second.get("id").asLong(), greaterThan(first.get("id").asLong()));
        assertEquals(first.get("slot").asInt(), second.get("slot").asInt());
    }

    @Test
    void theClientReservesSegmentsThroughTheServer() throws Exception {
        long first = ok(clientUrl + "/orders/next", null).get("id").asLong();
        assertThat(ok(clientUrl + "/orders/next", null).get("id").asLong(), greaterThan(first));
    }

    @Test
    void theServerIssuesIdsToATokenOnly() throws Exception {
        assertEquals(401, send(serverUrl + "/v5/ids/ticket", null).statusCode());
        JsonNode ids = ok(serverUrl + "/v5/ids/ticket?count=3", TOKEN).get("ids");
        assertEquals(3, ids.size());
        assertThat(ids.get(2).asLong(), greaterThan(ids.get(0).asLong()));
    }
}
