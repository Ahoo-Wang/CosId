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

package me.ahoo.cosid.store.tck;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Runs the concurrency cases of the contracts: every task starts at one barrier, on a platform thread of its own.
 */
final class Concurrently {
    /** Generous, as a loaded CI machine or a remote backend can be slow; a deadlock still fails the case. */
    static final Duration TIMEOUT = Duration.ofMinutes(2);

    private Concurrently() {
    }

    static <S> List<S> open(int count, Supplier<S> opener) {
        List<S> opened = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            opened.add(opener.get());
        }
        return opened;
    }

    static void closeAll(List<? extends AutoCloseable> closeables) {
        for (AutoCloseable closeable : closeables) {
            try {
                closeable.close();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to close " + closeable, e);
            }
        }
    }

    /**
     * Runs {@code task(0 .. threads - 1)} at once and returns the results in task order.
     */
    static <T> List<T> run(int threads, IntFunction<T> task) {
        CyclicBarrier start = new CyclicBarrier(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<T>> futures = new ArrayList<>(threads);
            for (int i = 0; i < threads; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    start.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                    return task.apply(index);
                }));
            }
            long deadline = System.nanoTime() + TIMEOUT.toNanos();
            List<T> results = new ArrayList<>(threads);
            for (Future<T> future : futures) {
                results.add(future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
            }
            return results;
        } catch (ExecutionException e) {
            throw new AssertionError("A concurrent task failed: " + e.getCause(), e.getCause());
        } catch (TimeoutException e) {
            throw new AssertionError("The concurrent tasks did not finish within " + TIMEOUT, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted", e);
        } finally {
            executor.shutdownNow();
        }
    }
}
