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

package me.ahoo.cosid.test.segment;

import me.ahoo.cosid.segment.IdSegmentDistributor;

import org.jspecify.annotations.NonNull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * In-memory {@link IdSegmentDistributor} that simulates network latency, for tests and benchmarks (moved from
 * {@code IdSegmentDistributor.Mock} in 4.0).
 *
 * <p>This implementation simulates network I/O delays to test the
 * behavior of ID segment distributors under realistic conditions.
 */
public class MockIdSegmentDistributor implements IdSegmentDistributor {
    private static final AtomicInteger MOCK_COUNTER = new AtomicInteger();
    private final long step;
    private final String name;
    private final long ioWaiting;
    private final AtomicLong adder = new AtomicLong();

    /**
     * Create a new Mock distributor with default configuration.
     */
    public MockIdSegmentDistributor() {
        this(IdSegmentDistributor.DEFAULT_STEP, 220000);
    }

    /**
     * Create a new Mock distributor with custom configuration.
     *
     * @param step The step size for ID segments
     * @param tps  The transactions per second to simulate for I/O waiting
     */
    public MockIdSegmentDistributor(long step, int tps) {
        this.step = step;
        this.ioWaiting = TimeUnit.SECONDS.toNanos(1) / tps;
        this.name = "mock__" + MOCK_COUNTER.incrementAndGet();
    }

    /**
     * Get the namespace for this distributor.
     *
     * @return The namespace
     */
    @Override
    public @NonNull String getNamespace() {
        return "__";
    }

    /**
     * Get the name for this distributor.
     *
     * @return The name
     */
    @Override
    public @NonNull String getName() {
        return name;
    }

    /**
     * Get the step size for ID segments.
     *
     * @return The step size
     */
    @Override
    public long getStep() {
        return step;
    }

    /**
     * Allocate the next maximum ID for the specified step size.
     *
     * <p>This method simulates network I/O delay before allocating the ID.
     *
     * @param step The step size for allocation
     * @return The maximum ID in the allocated range
     */
    @Override
    public long nextMaxId(long step) {
        LockSupport.parkNanos(ioWaiting);
        return adder.addAndGet(step);
    }

}
