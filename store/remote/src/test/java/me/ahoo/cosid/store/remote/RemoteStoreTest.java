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

package me.ahoo.cosid.store.remote;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.NamespaceMismatchException;
import me.ahoo.cosid.RangeExhaustedException;
import me.ahoo.cosid.RangeSealedException;
import me.ahoo.cosid.lease.AcquireReason;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.store.StoreTimeoutException;
import me.ahoo.cosid.store.StoreTimeoutException;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * How the client maps the answers of the HTTP API to the outcomes of the primitives, against a stub server
 * answering each path with a canned status and body. The full contracts run in server/ against a real server.
 */
class RemoteStoreTest {
    private static final UUID INCARNATION = UUID.fromString("6f1c7c2a-0d7e-4c35-9a53-2a3f3d1f0b11");
    private static final Owner OWNER = new Owner("host:8080", INCARNATION);
    private static final SlotLease LEASE = new SlotLease("ns", 3, OWNER, 7, 2_000, 1_000, 3_000, 1_500);
    private static final String LEASE_JSON = "\"namespace\":\"ns\",\"slot\":3,\"ownerKey\":\"host:8080\",\"incarnation\":\"" + INCARNATION
        + "\",\"fence\":7,\"expiresAt\":2000,\"floor\":1000,\"ceiling\":3000,\"storeNow\":1500";

    /** A canned answer; with a {@code hold}, the stub writes it only once the test opens the latch. */
    private record Answer(int status, String body, CountDownLatch hold) {
    }

    private final Map<String, Answer> answers = new ConcurrentHashMap<>();
    private final Map<String, String> requests = new ConcurrentHashMap<>();
    private final Map<String, String> authorizations = new ConcurrentHashMap<>();
    private final ExecutorService handlers = Executors.newCachedThreadPool();
    /** Opened after each test, so that no stub handler stays held. */
    private final CountDownLatch release = new CountDownLatch(1);
    private HttpServer server;
    private RemoteStore store;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requests.put(path, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorizations.put(path, String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            Answer answer = answers.getOrDefault(path, new Answer(404, "{\"code\":\"not_found\",\"message\":\"no stub\"}", null));
            if (answer.hold() != null) {
                try {
                    answer.hold().await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] body = answer.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(answer.status(), body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
            exchange.close();
        });
        // A pool of its own: an answer held for one test never delays the next request.
        server.setExecutor(handlers);
        server.start();
        store = new RemoteStore(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), Optional.of("secret"),
            Duration.ofSeconds(2), Duration.ofSeconds(30));
    }

    @AfterEach
    void stop() {
        release.countDown();
        store.close();
        server.stop(0);
        handlers.shutdownNow();
    }

    private void answer(String path, int status, String body) {
        answers.put(path, new Answer(status, body, null));
    }

    private static String error(String code) {
        return "{\"code\":\"" + code + "\",\"message\":\"stubbed " + code + "\"}";
    }

    @Test
    void reserveMapsRangesAndErrors() {
        answer(RemoteApi.RESERVE, 200, "{\"start\":9007199254740993,\"end\":9007199254741003}");
        assertEquals(new Range(9007199254740993L, 9007199254741003L), store.ranges().reserve("ns/a", 10, 5));
        assertEquals("{\"key\":\"ns/a\",\"size\":10,\"initialOffset\":5}", requests.get(RemoteApi.RESERVE));
        assertEquals("Bearer secret", authorizations.get(RemoteApi.RESERVE));

        answer(RemoteApi.RESERVE, 410, error(RemoteApi.RANGE_SEALED));
        assertEquals("ns/a", assertThrows(RangeSealedException.class, () -> store.ranges().reserve("ns/a", 1, 0)).key());
        answer(RemoteApi.RESERVE, 422, error(RemoteApi.RANGE_EXHAUSTED));
        assertThrows(RangeExhaustedException.class, () -> store.ranges().reserve("ns/a", 1, 0));
        answer(RemoteApi.RESERVE, 429, error(RemoteApi.TOO_MANY_KEYS));
        RemoteStoreException tooMany = assertThrows(RemoteStoreException.class, () -> store.ranges().reserve("ns/a", 1, 0));
        assertEquals(429, tooMany.status());
        assertEquals(RemoteApi.TOO_MANY_KEYS, tooMany.code());
        assertThat(tooMany.getMessage(), containsString("stubbed too_many_keys"));
        answer(RemoteApi.RESERVE, 504, error(RemoteApi.STORE_TIMEOUT));
        RemoteStoreException timedOut = assertThrows(RemoteStoreException.class, () -> store.ranges().reserve("ns/a", 1, 0));
        assertEquals(504, timedOut.status());
        assertTrue(timedOut.transientFailure());
        assertInstanceOf(StoreTimeoutException.class, timedOut.getCause(), "the server's store timed out: a store timeout here too");
        assertTrue(StoreTimeoutException.isTimeout(timedOut));
        answer(RemoteApi.RESERVE, 500, error(RemoteApi.STORE_ERROR));
        assertFalse(StoreTimeoutException.isTimeout(assertThrows(RemoteStoreException.class, () -> store.ranges().reserve("ns/a", 1, 0))));
        answer(RemoteApi.RESERVE, 200, "{\"start\":\"1\",\"end\":2}");
        assertThat(assertThrows(RemoteStoreException.class, () -> store.ranges().reserve("ns/a", 1, 0)).getMessage(), containsString("not an integer"));
    }

    @Test
    void sealMapsUnsupportedToFalse() {
        answer(RemoteApi.SEAL, 204, "");
        assertTrue(store.ranges().seal("ns/a"));
        answer(RemoteApi.SEAL, 501, error(RemoteApi.SEAL_UNSUPPORTED));
        assertFalse(store.ranges().seal("ns/a"));
        answer(RemoteApi.SEAL, 403, error(RemoteApi.FORBIDDEN));
        assertEquals(403, assertThrows(RemoteStoreException.class, () -> store.ranges().seal("ns/a")).status());
    }

    @Test
    void acquireMapsLeasesExhaustionAndMismatch() {
        answer(RemoteApi.ACQUIRE, 200, "{" + LEASE_JSON + ",\"reason\":\"STICKY\",\"duplicateOwnerKey\":true}");
        AcquireResult.Acquired acquired = assertInstanceOf(AcquireResult.Acquired.class,
            store.slots().acquire("ns", OWNER, 8, Duration.ofSeconds(30), Duration.ofSeconds(1), OptionalInt.of(3)));
        assertEquals(LEASE, acquired.lease());
        assertEquals(AcquireReason.STICKY, acquired.reason());
        assertTrue(acquired.duplicateOwnerKey());
        assertEquals("{\"namespace\":\"ns\",\"ownerKey\":\"host:8080\",\"incarnation\":\"" + INCARNATION
            + "\",\"limit\":8,\"ttlMs\":30000,\"toleranceMs\":1000,\"preferredSlot\":3}", requests.get(RemoteApi.ACQUIRE));
        store.slots().acquire("ns", OWNER, 8, Duration.ofSeconds(30), Duration.ofSeconds(1), OptionalInt.empty());
        assertFalse(requests.get(RemoteApi.ACQUIRE).contains("preferredSlot"));

        answer(RemoteApi.ACQUIRE, 409, "{\"code\":\"slots_exhausted\",\"message\":\"full\",\"earliestExpiresAt\":12345}");
        assertEquals(new AcquireResult.Exhausted(12345), store.slots().acquire("ns", OWNER, 8, Duration.ofSeconds(30), Duration.ofSeconds(1), OptionalInt.empty()));

        answer(RemoteApi.ACQUIRE, 409, error(RemoteApi.NAMESPACE_MISMATCH));
        NamespaceMismatchException mismatch = assertThrows(NamespaceMismatchException.class,
            () -> store.slots().acquire("ns", OWNER, 8, Duration.ofSeconds(30), Duration.ofSeconds(1), OptionalInt.empty()));
        assertEquals("ns", mismatch.namespace());
        answer(RemoteApi.ACQUIRE, 409, "{\"code\":\"namespace_mismatch\"}");
        assertThrows(NamespaceMismatchException.class,
            () -> store.slots().acquire("ns", OWNER, 8, Duration.ofSeconds(30), Duration.ofSeconds(1), OptionalInt.empty()));

        answer(RemoteApi.ACQUIRE, 401, error(RemoteApi.UNAUTHORIZED));
        assertEquals(RemoteApi.UNAUTHORIZED, assertThrows(RemoteStoreException.class,
            () -> store.slots().acquire("ns", OWNER, 8, Duration.ofSeconds(30), Duration.ofSeconds(1), OptionalInt.empty())).code());
        answer(RemoteApi.ACQUIRE, 200, "{" + LEASE_JSON + ",\"reason\":7}");
        assertThat(assertThrows(RemoteStoreException.class,
            () -> store.slots().acquire("ns", OWNER, 8, Duration.ofSeconds(30), Duration.ofSeconds(1), OptionalInt.empty())).getMessage(),
            containsString("not a string"));
    }

    @Test
    void renewMapsLostToEmpty() {
        answer(RemoteApi.RENEW, 200, "{" + LEASE_JSON + "}");
        assertEquals(Optional.of(LEASE), store.slots().renew(LEASE, Duration.ofSeconds(30), Duration.ofSeconds(1)));
        assertEquals("{" + LEASE_JSON + ",\"ttlMs\":30000,\"toleranceMs\":1000}", requests.get(RemoteApi.RENEW));
        answer(RemoteApi.RENEW, 409, error(RemoteApi.LEASE_LOST));
        assertEquals(Optional.empty(), store.slots().renew(LEASE, Duration.ofSeconds(30), Duration.ofSeconds(1)));
        answer(RemoteApi.RENEW, 500, error(RemoteApi.STORE_ERROR));
        assertEquals(500, assertThrows(RemoteStoreException.class, () -> store.slots().renew(LEASE, Duration.ofSeconds(30), Duration.ofSeconds(1))).status());
    }

    @Test
    void releaseMapsLostToFalse() {
        answer(RemoteApi.RELEASE, 204, "");
        assertTrue(store.slots().release(LEASE, 2_500));
        assertEquals("{" + LEASE_JSON + ",\"lastIssued\":2500}", requests.get(RemoteApi.RELEASE));
        answer(RemoteApi.RELEASE, 409, error(RemoteApi.LEASE_LOST));
        assertFalse(store.slots().release(LEASE, 2_500));
        answer(RemoteApi.RELEASE, 409, error(RemoteApi.NAMESPACE_MISMATCH));
        assertThrows(NamespaceMismatchException.class, () -> store.slots().release(LEASE, 2_500));
        answer(RemoteApi.RELEASE, 503, error(RemoteApi.BUSY));
        RemoteStoreException busy = assertThrows(RemoteStoreException.class, () -> store.slots().release(LEASE, 2_500));
        assertTrue(busy.transientFailure());
    }

    @Test
    void failuresWithoutAJsonAnswer() {
        answer(RemoteApi.RESERVE, 502, "<html>bad gateway</html>");
        RemoteStoreException html = assertThrows(RemoteStoreException.class, () -> store.ranges().reserve("ns/a", 1, 0));
        assertEquals(502, html.status(), html::getMessage);
        assertNull(html.code());
        assertThat(html.getMessage(), containsString("without a JSON body"));
        assertFalse(StoreTimeoutException.isTimeout(html), "an error answer is no store timeout");

        answer(RemoteApi.RESERVE, 500, "");
        assertThat(assertThrows(RemoteStoreException.class, () -> store.ranges().reserve("ns/a", 1, 0)).getMessage(), containsString("HTTP 500"));

    }

    @Test
    void unansweredCallTimesOut() {
        answers.put(RemoteApi.RESERVE, new Answer(200, "{\"start\":0,\"end\":1}", release));
        RemoteStoreException timeout;
        try (RemoteStore impatient = new RemoteStore(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), Optional.empty(),
            Duration.ofSeconds(2), Duration.ofMillis(200))) {
            timeout = assertThrows(RemoteStoreException.class, () -> impatient.ranges().reserve("ns/a", 1, 0));
        }
        assertEquals(0, timeout.status(), timeout::getMessage);
        assertTrue(timeout.transientFailure());
        assertThat(timeout.getMessage(), containsString("timed out"));
        assertTrue(StoreTimeoutException.isTimeout(timeout), "the runtime reports it as a store timeout");
    }

    @Test
    void unreachableServerIsTransient() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        try (RemoteStore unreachable = new RemoteStore(URI.create("http://127.0.0.1:" + port), Optional.empty(), Duration.ofSeconds(1), Duration.ofSeconds(1))) {
            RemoteStoreException error = assertThrows(RemoteStoreException.class, () -> unreachable.ranges().reserve("ns/a", 1, 0));
            assertTrue(error.transientFailure());
            assertNull(error.code());
        }
    }

    @Test
    void sendsNoAuthorizationWithoutAToken() {
        answer(RemoteApi.SEAL, 204, "");
        try (RemoteStore anonymous = new RemoteStore(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), Optional.of(" "),
            Duration.ofSeconds(2), Duration.ofSeconds(30))) {
            assertTrue(anonymous.ranges().seal("ns/a"));
        }
        assertEquals("null", authorizations.get(RemoteApi.SEAL));
    }

    @Test
    void interruptedCallKeepsTheInterrupt() {
        answers.put(RemoteApi.RESERVE, new Answer(200, "{\"start\":0,\"end\":1}", release));
        Thread.currentThread().interrupt();
        try {
            RemoteStoreException error = assertThrows(RemoteStoreException.class, () -> store.ranges().reserve("ns/a", 1, 0));
            assertThat(error.getMessage(), containsString("interrupted"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
