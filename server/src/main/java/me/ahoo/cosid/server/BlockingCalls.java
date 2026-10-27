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

import me.ahoo.cosid.store.remote.RemoteApi;

import org.springframework.http.HttpStatus;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * The bounded pool the blocking store and generator calls run on (08-remote.md §2), off the servlet threads: when its
 * threads are busy and its queue is full, the call is a {@code 503} with {@code Retry-After: 1}.
 */
final class BlockingCalls implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final Rejections rejections;

    BlockingCalls(ServerProperties.Blocking blocking, Rejections rejections) {
        this.rejections = rejections;
        AtomicInteger threads = new AtomicInteger();
        BlockingQueue<Runnable> queue = blocking.queueCapacity() == 0 ? new SynchronousQueue<>() : new ArrayBlockingQueue<>(blocking.queueCapacity());
        this.executor = new ThreadPoolExecutor(blocking.threads(), blocking.threads(), 60, TimeUnit.SECONDS, queue, runnable -> {
            Thread thread = new Thread(runnable, "cosid-server-" + threads.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
        this.executor.allowCoreThreadTimeOut(true);
    }

    <T> CompletableFuture<T> submit(Supplier<T> call) {
        try {
            return CompletableFuture.supplyAsync(call, executor);
        } catch (RejectedExecutionException e) {
            rejections.record(Rejections.BUSY);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, RemoteApi.BUSY, "The server is busy; retry later");
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
