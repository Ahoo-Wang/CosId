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

import me.ahoo.cosid.store.Store;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * The backing store's health, in the readiness group (R1-server-02): one reserve of size 1 on {@link #KEY}, the
 * lightest call every store has, within {@link #TIMEOUT}. At most one probe runs at a time, on a thread of its own,
 * and its result serves the calls of the next {@link #REUSE}: health is public, so a caller cannot make the server
 * write to the store faster than that, and a store that hangs holds one thread, not one per probe.
 */
final class StoreHealthIndicator implements HealthIndicator, AutoCloseable {
    /** The range key of the probe; its offset grows by one per probe. */
    static final String KEY = "cosid-server/readiness";
    static final Duration TIMEOUT = Duration.ofSeconds(2);
    static final Duration REUSE = Duration.ofSeconds(1);

    private final Store store;
    private final LongSupplier nanoTime;
    private final ExecutorService probes = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "cosid-server-readiness");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<Probe> last = new AtomicReference<>();

    StoreHealthIndicator(Store store) {
        this(store, System::nanoTime);
    }

    StoreHealthIndicator(Store store, LongSupplier nanoTime) {
        this.store = store;
        this.nanoTime = nanoTime;
    }

    private record Probe(long started, CompletableFuture<Health> result) {
    }

    @Override
    public Health health() {
        long now = nanoTime.getAsLong();
        Probe probe = last.get();
        if (probe == null || probe.result().isDone() && now - probe.started() >= REUSE.toNanos()) {
            Probe next = new Probe(now, new CompletableFuture<>());
            if (last.compareAndSet(probe, next)) {
                probes.execute(() -> next.result().complete(probe()));
                probe = next;
            } else {
                probe = last.get();
            }
        }
        long left = probe.started() + TIMEOUT.toNanos() - now;
        try {
            return probe.result().get(Math.max(left, 0), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            return Health.down().withDetail("error", "no answer within " + TIMEOUT.toMillis() + "ms").build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Health.unknown().build();
        } catch (ExecutionException e) {
            return Health.down().withDetail("error", e.getCause().getClass().getSimpleName()).build();
        }
    }

    private Health probe() {
        try {
            store.ranges().reserve(KEY, 1, 0);
            return Health.up().build();
        } catch (RuntimeException e) {
            // The type only: the message may hold an address, and health is public.
            return Health.down().withDetail("error", e.getClass().getSimpleName()).build();
        }
    }

    @Override
    public void close() {
        probes.shutdownNow();
    }
}
