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

package me.ahoo.cosid.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.proxy.api.SegmentClient;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class SegmentClientTest {
    @Test
    public void createDistributorUsesPostPathAndQueryContract() throws IOException {
        CapturingHttpServer server = new CapturingHttpServer(200, "");
        SegmentClient segmentClient = ApiClientFactory.createSegmentClient(server.baseUrl());

        segmentClient.createDistributor("SegmentClientTest", "test", 0, 1);

        assertEquals("POST", server.requestMethod());
        assertEquals("/segments/distributor/SegmentClientTest/test", server.requestPath());
        assertEquals("0", server.queryParameters().get("offset"));
        assertEquals("1", server.queryParameters().get("step"));

        server.stop();
    }

    @Test
    public void nextMaxIdUsesPatchPathAndReturnsResponseBody() throws IOException {
        CapturingHttpServer server = new CapturingHttpServer(200, "41", "application/json");
        SegmentClient segmentClient = ApiClientFactory.createSegmentClient(server.baseUrl());

        long nextMaxId = segmentClient.nextMaxId("SegmentClientTest", "test", 10);

        assertEquals(41, nextMaxId);
        assertEquals("PATCH", server.requestMethod());
        assertEquals("/segments/SegmentClientTest/test", server.requestPath());
        assertEquals("10", server.queryParameters().get("step"));
        assertTrue(server.queryParameters().containsKey("step"));

        server.stop();
    }

    private static class CapturingHttpServer {
        private final HttpServer server;
        private final AtomicReference<String> requestMethod = new AtomicReference<>();
        private final AtomicReference<String> requestPath = new AtomicReference<>();
        private final AtomicReference<String> requestQuery = new AtomicReference<>();

        CapturingHttpServer(int status, String responseBody) throws IOException {
            this(status, responseBody, "text/plain;charset=UTF-8");
        }

        CapturingHttpServer(int status, String responseBody, String contentType) throws IOException {
            this.server = HttpServer.create(new InetSocketAddress(0), 0);
            this.server.createContext("/", exchange -> {
                requestMethod.set(exchange.getRequestMethod());
                requestPath.set(exchange.getRequestURI().getPath());
                requestQuery.set(exchange.getRequestURI().getRawQuery());
                byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", contentType);
                exchange.sendResponseHeaders(status, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
                exchange.close();
            });
            this.server.start();
        }

        String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        String requestMethod() {
            return requestMethod.get();
        }

        String requestPath() {
            return requestPath.get();
        }

        Map<String, String> queryParameters() {
            return Arrays.stream(requestQuery.get().split("&"))
                .map(queryPart -> queryPart.split("=", 2))
                .collect(Collectors.toMap(queryPart -> queryPart[0], queryPart -> queryPart[1]));
        }

        void stop() {
            server.stop(0);
        }
    }
}
