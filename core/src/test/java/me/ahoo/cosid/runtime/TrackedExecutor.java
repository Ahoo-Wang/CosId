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

package me.ahoo.cosid.runtime;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A virtual thread executor standing in for the runtime's own one: counts the tasks still running and records its
 * shutdown in the events.
 */
final class TrackedExecutor extends AbstractExecutorService {
    final ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
    final AtomicInteger running = new AtomicInteger();
    final List<String> events;
    volatile boolean reject;

    TrackedExecutor(List<String> events) {
        this.events = events;
    }

    @Override
    public void execute(Runnable command) {
        if (reject) {
            throw new RejectedExecutionException("rejecting");
        }
        running.incrementAndGet();
        delegate.execute(() -> {
            try {
                command.run();
            } finally {
                running.decrementAndGet();
            }
        });
    }

    @Override
    public void shutdown() {
        events.add("executor:shutdown");
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        events.add("executor:shutdown");
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }
}
