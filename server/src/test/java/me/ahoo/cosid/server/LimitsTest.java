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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.remote.RemoteApi;
import me.ahoo.cosid.store.remote.RemoteStore;
import me.ahoo.cosid.store.remote.RemoteStoreException;
import me.ahoo.cosid.store.tck.InMemoryStore;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The limits of 08-remote.md §2: 400 for fields out of their limits, 429 past the per-namespace key cap, 503 with
 * {@code Retry-After: 1} when the blocking pool is full (and the {@code cosid.server.call.rejections} meter), and the
 * other errors as {@code {code, message}}.
 */
class LimitsTest {
    private static final String INCARNATION = UUID.randomUUID().toString();
    private static final ScriptedStore STORE = new ScriptedStore();
    private static TestServer server;

    @BeforeAll
    static void start() {
        server = TestServer.memory(STORE,
            "cosid.server.limits.max-keys-per-namespace=2",
            "cosid.server.limits.max-reserve=1000",
            "cosid.server.limits.max-slot-limit=64",
            "cosid.server.blocking.threads=1",
            "cosid.server.blocking.queue-capacity=0");
    }

    @AfterAll
    static void stop() {
        server.close();
    }

    private static HttpResponse<String> post(String path, String json) {
        return server.post(path, json, TestServer.TOKEN);
    }

    private static void assertBadRequest(String path, String json, String message) {
        HttpResponse<String> response = post(path, json);
        assertEquals(400, response.statusCode(), () -> json + " -> " + response.body());
        assertThat(response.body(), containsString("\"code\":\"" + RemoteApi.BAD_REQUEST + "\""));
        assertThat(response.body(), containsString(message));
    }

    private static String acquire(String fields) {
        return "{\"namespace\":\"ns\",\"ownerKey\":\"host\",\"incarnation\":\"" + INCARNATION + "\",\"limit\":8,\"ttlMs\":1000,\"toleranceMs\":100" + fields + "}";
    }

    @Test
    void rangeFieldsOutOfTheirLimitsAre400() {
        assertBadRequest(RemoteApi.RESERVE, "{\"key\":\"a//b\",\"size\":1,\"initialOffset\":0}", "segments");
        assertBadRequest(RemoteApi.RESERVE, "{\"key\":\"a/../b\",\"size\":1,\"initialOffset\":0}", "'..'");
        assertBadRequest(RemoteApi.RESERVE, "{\"key\":\"a b\",\"size\":1,\"initialOffset\":0}", "segments");
        assertBadRequest(RemoteApi.RESERVE, "{\"key\":\"" + "k".repeat(256) + "\",\"size\":1,\"initialOffset\":0}", "255");
        assertBadRequest(RemoteApi.RESERVE, "{\"size\":1,\"initialOffset\":0}", "key is required");
        assertBadRequest(RemoteApi.RESERVE, "{\"key\":\"a/b\",\"size\":1001,\"initialOffset\":0}", "size must be in [1, 1000]");
        assertBadRequest(RemoteApi.RESERVE, "{\"key\":\"a/b\",\"size\":0,\"initialOffset\":0}", "size");
        assertBadRequest(RemoteApi.RESERVE, "{\"key\":\"a/b\",\"size\":1,\"initialOffset\":-1}", "initialOffset must not be negative");
        assertBadRequest(RemoteApi.RESERVE, "{\"key\":", "not the expected JSON");
        assertBadRequest(RemoteApi.RESERVE, "{\"key\":\"a/b\",\"size\":\"many\",\"initialOffset\":0}", "not the expected JSON");
        assertBadRequest(RemoteApi.SEAL, "{}", "key is required");
        HttpResponse<String> text = server.send(HttpRequest.newBuilder(server.baseUrl().resolve(RemoteApi.RESERVE))
            .header("Authorization", "Bearer " + TestServer.TOKEN).header("Content-Type", "text/plain")
            .POST(HttpRequest.BodyPublishers.ofString("key")).build());
        assertEquals(400, text.statusCode());
    }

    @Test
    void slotFieldsOutOfTheirLimitsAre400() {
        assertBadRequest(RemoteApi.ACQUIRE, acquire("").replace("\"ns\"", "\"a/b\""), "namespace must be");
        assertBadRequest(RemoteApi.ACQUIRE, acquire("").replace("\"ttlMs\":1000", "\"ttlMs\":10"), "ttlMs must be in [100, 3600000]");
        assertBadRequest(RemoteApi.ACQUIRE, acquire("").replace("\"ttlMs\":1000", "\"ttlMs\":3600001"), "ttlMs");
        assertBadRequest(RemoteApi.ACQUIRE, acquire("").replace("\"toleranceMs\":100", "\"toleranceMs\":-1"), "toleranceMs must be in [0, 60000]");
        assertBadRequest(RemoteApi.ACQUIRE, acquire("").replace("\"limit\":8", "\"limit\":65"), "limit must be in [1, 64]");
        assertBadRequest(RemoteApi.ACQUIRE, acquire("").replace(INCARNATION, "nope"), "incarnation must be a UUID");
        assertBadRequest(RemoteApi.ACQUIRE, acquire("").replace("\"host\"", "\"" + "h".repeat(256) + "\""), "ownerKey");
        assertBadRequest(RemoteApi.ACQUIRE, acquire(",\"preferredSlot\":8"), "preferredSlot must be in [0, limit)");
        assertBadRequest(RemoteApi.ACQUIRE, acquire(",\"preferredSlot\":-1"), "preferredSlot");
        assertBadRequest(RemoteApi.RENEW, "{\"namespace\":\"ns\",\"slot\":-1,\"ownerKey\":\"h\",\"incarnation\":\"" + INCARNATION
            + "\",\"fence\":1,\"ttlMs\":1000,\"toleranceMs\":0}", "slot must not be negative");
        assertBadRequest(RemoteApi.RENEW, "{\"namespace\":\"ns\",\"slot\":0,\"ownerKey\":\"h\",\"incarnation\":\"" + INCARNATION
            + "\",\"ttlMs\":1000,\"toleranceMs\":0}", "fence is required");
        assertBadRequest(RemoteApi.RELEASE, "{\"namespace\":\"ns\",\"slot\":0,\"ownerKey\":\"h\",\"incarnation\":\"" + INCARNATION
            + "\",\"fence\":1}", "lastIssued is required");
    }

    @Test
    void keyCapPerNamespaceIs429() {
        try (RemoteStore client = server.client()) {
            client.ranges().reserve("capped/a", 1, 0);
            client.ranges().reserve("capped/b", 1, 0);
            client.ranges().reserve("capped/a", 1, 0);
            RemoteStoreException error = assertThrows(RemoteStoreException.class, () -> client.ranges().reserve("capped/c", 1, 0));
            assertEquals(429, error.status());
            assertEquals(RemoteApi.TOO_MANY_KEYS, error.code());
            client.ranges().reserve("other/c", 1, 0);
        }
        assertThat(prometheus(), containsString("reason=\"too_many_keys\""));
    }

    @Test
    void fullBlockingPoolIs503WithRetryAfter() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        STORE.blockOn(entered, release);
        try {
            CompletableFuture<HttpResponse<String>> first = server.http().sendAsync(HttpRequest.newBuilder(server.baseUrl().resolve(RemoteApi.RESERVE))
                .header("Authorization", "Bearer " + TestServer.TOKEN).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"key\":\"busy/a\",\"size\":1,\"initialOffset\":0}")).build(), HttpResponse.BodyHandlers.ofString());
            assertTrue(entered.await(10, TimeUnit.SECONDS), "the first call occupies the only thread");

            HttpResponse<String> rejected = post(RemoteApi.RESERVE, "{\"key\":\"busy/a\",\"size\":1,\"initialOffset\":0}");
            assertEquals(503, rejected.statusCode());
            assertEquals("1", rejected.headers().firstValue("Retry-After").orElseThrow());
            assertThat(rejected.body(), containsString("\"code\":\"" + RemoteApi.BUSY + "\""));
            try (RemoteStore client = server.client()) {
                RemoteStoreException error = assertThrows(RemoteStoreException.class, () -> client.ranges().reserve("busy/a", 1, 0));
                assertEquals(503, error.status());
                assertTrue(error.transientFailure());
            }
            release.countDown();
            assertEquals(200, first.get(10, TimeUnit.SECONDS).statusCode());
        } finally {
            release.countDown();
            STORE.blockOn(null, null);
        }
        assertThat(prometheus(), containsString("cosid_server_call_rejections_total{reason=\"busy\"}"));
    }

    @Test
    void unsupportedSealIs501AndTheClientSaysFalse() {
        try (RemoteStore client = server.client()) {
            assertFalse(client.ranges().seal("unsealable/a"));
        }
        HttpResponse<String> response = post(RemoteApi.SEAL, "{\"key\":\"unsealable/a\"}");
        assertEquals(501, response.statusCode());
        assertThat(response.body(), containsString(RemoteApi.SEAL_UNSUPPORTED));
    }

    @Test
    void storeFailureIs500() {
        try (RemoteStore client = server.client()) {
            RemoteStoreException error = assertThrows(RemoteStoreException.class, () -> client.ranges().reserve("fail/a", 1, 0));
            assertEquals(500, error.status());
            assertEquals(RemoteApi.STORE_ERROR, error.code());
            assertThat(error.getMessage(), containsString("backend down"));
            RemoteStoreException unsupported = assertThrows(RemoteStoreException.class, () -> client.slots().acquire("unsupported", new Owner("h", UUID.randomUUID()), 1,
                Duration.ofSeconds(1), Duration.ZERO, OptionalInt.empty()));
            assertEquals(501, unsupported.status());
            assertEquals(RemoteApi.UNSUPPORTED, unsupported.code());
        }
    }

    @Test
    void unknownPathsAndMethodsAnswerTheErrorBody() {
        HttpResponse<String> missing = server.get("/v5/nothing", TestServer.TOKEN);
        assertEquals(404, missing.statusCode());
        assertThat(missing.body(), containsString("\"code\":\"" + RemoteApi.NOT_FOUND + "\""));
        assertEquals(404, server.get(RemoteApi.IDS, TestServer.TOKEN).statusCode(), "the id endpoints are off by default");
        HttpResponse<String> method = server.get(RemoteApi.RESERVE, TestServer.TOKEN);
        assertEquals(405, method.statusCode());
        assertThat(method.body(), containsString(RemoteApi.METHOD_NOT_ALLOWED));
    }

    @Test
    void servesOpenApiAndActuator() {
        HttpResponse<String> api = server.get("/v3/api-docs", TestServer.TOKEN);
        assertEquals(200, api.statusCode());
        assertThat(api.body(), containsString(RemoteApi.RESERVE));
        assertThat(api.body(), containsString(RemoteApi.ACQUIRE));
        assertThat(api.body(), containsString("bearer"));
        assertEquals(200, server.get("/actuator/cosid", TestServer.TOKEN).statusCode());
        assertEquals(200, server.get("/actuator/info", TestServer.TOKEN).statusCode());
    }

    private static String prometheus() {
        HttpResponse<String> response = server.get("/actuator/prometheus", TestServer.TOKEN);
        assertEquals(200, response.statusCode(), response::body);
        return response.body();
    }

    /**
     * An in-memory store whose ranges block while a test holds them, fail for {@code fail/*} keys and cannot seal; its
     * slots fail with UnsupportedOperationException in the namespace {@code unsupported}.
     */
    static final class ScriptedStore implements Store {
        private final InMemoryStore memory = new InMemoryStore();
        private volatile CountDownLatch entered;
        private volatile CountDownLatch release;

        void blockOn(CountDownLatch entered, CountDownLatch release) {
            this.entered = entered;
            this.release = release;
        }

        @Override
        public RangeStore ranges() {
            return new RangeStore() {
                @Override
                public Range reserve(String key, long size, long initialOffset) {
                    CountDownLatch gate = release;
                    if (gate != null) {
                        entered.countDown();
                        try {
                            gate.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    if (key.startsWith("fail/")) {
                        throw new IllegalStateException("backend down");
                    }
                    return memory.reserve(key, size, initialOffset);
                }
            };
        }

        @Override
        public SlotLeaseStore slots() {
            return new SlotLeaseStore() {
                @Override
                public AcquireResult acquire(String namespace, Owner owner, int limit, Duration ttl, Duration tolerance, OptionalInt preferredSlot) {
                    if ("unsupported".equals(namespace)) {
                        throw new UnsupportedOperationException("no slots here");
                    }
                    return memory.acquire(namespace, owner, limit, ttl, tolerance, preferredSlot);
                }

                @Override
                public Optional<SlotLease> renew(SlotLease lease, Duration ttl, Duration tolerance) {
                    return memory.renew(lease, ttl, tolerance);
                }

                @Override
                public boolean release(SlotLease lease, long lastIssued) {
                    return memory.release(lease, lastIssued);
                }
            };
        }

        @Override
        public void close() {
        }
    }
}
