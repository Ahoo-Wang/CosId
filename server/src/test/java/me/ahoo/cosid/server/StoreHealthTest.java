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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.tck.InMemoryStore;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** R1-server-02: readiness asks the backing store, liveness does not. */
class StoreHealthTest {
    /** A store whose reserves fail while {@link #down} is set, or wait on {@link #gate}, counting them. */
    static final class SwitchedStore implements Store {
        final InMemoryStore memory = new InMemoryStore();
        final AtomicBoolean down = new AtomicBoolean();
        final AtomicInteger reserves = new AtomicInteger();
        volatile CountDownLatch gate;

        @Override
        public RangeStore ranges() {
            return (key, size, initialOffset) -> {
                reserves.incrementAndGet();
                CountDownLatch waiting = gate;
                if (waiting != null) {
                    try {
                        waiting.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                if (down.get()) {
                    throw new IllegalStateException("backend down at redis://10.0.0.7:6379");
                }
                return memory.reserve(key, size, initialOffset);
            };
        }

        @Override
        public SlotLeaseStore slots() {
            return memory;
        }

        @Override
        public void close() {
        }
    }

    @Test
    void readinessFollowsTheBackingStore() throws Exception {
        SwitchedStore store = new SwitchedStore();
        try (TestServer server = TestServer.memory(store)) {
            assertEquals(200, server.get("/actuator/health/readiness", null).statusCode());
            // The contributor of the readiness group, by the name a readiness group of one's own includes.
            assertEquals(200, server.get("/actuator/health/cosidServerStore", null).statusCode());
            store.down.set(true);
            Thread.sleep(StoreHealthIndicator.REUSE.toMillis() + 100);
            assertEquals(503, server.get("/actuator/health/readiness", null).statusCode(), "the store is down");
            assertEquals(200, server.get("/actuator/health/liveness", null).statusCode(), "liveness does not ask the store");
            store.down.set(false);
            Thread.sleep(StoreHealthIndicator.REUSE.toMillis() + 100);
            assertEquals(200, server.get("/actuator/health/readiness", null).statusCode());
        }
    }

    @Test
    void probesReuseOneResultAndTimeOut() {
        SwitchedStore store = new SwitchedStore();
        AtomicLong now = new AtomicLong();
        try (StoreHealthIndicator health = new StoreHealthIndicator(store, now::get)) {
            assertEquals(Status.UP, health.health().getStatus());
            assertEquals(Status.UP, health.health().getStatus());
            assertEquals(1, store.reserves.get(), "a second call within REUSE reuses the result");

            store.down.set(true);
            now.addAndGet(StoreHealthIndicator.REUSE.toNanos());
            assertEquals(Status.DOWN, health.health().getStatus());
            assertEquals("IllegalStateException", health.health().getDetails().get("error"), "the type only, not the address");

            store.down.set(false);
            store.gate = new CountDownLatch(1);
            now.addAndGet(StoreHealthIndicator.REUSE.toNanos());
            long started = System.nanoTime();
            assertEquals(Status.DOWN, health.health().getStatus(), "no answer within the timeout");
            assertTrue(System.nanoTime() - started >= StoreHealthIndicator.TIMEOUT.toNanos() - TimeUnit.MILLISECONDS.toNanos(50));
            now.addAndGet(StoreHealthIndicator.TIMEOUT.toNanos());
            assertEquals(Status.DOWN, health.health().getStatus(), "the hung probe answers at once");
            assertEquals(3, store.reserves.get(), "one probe at a time");
            store.gate.countDown();
        }
    }
}
