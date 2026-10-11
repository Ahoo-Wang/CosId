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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory {@link IdSegmentDistributor} for tests and benchmarks (moved from {@code IdSegmentDistributor.Atomic} in 4.0).
 *
 * <p>This implementation uses atomic operations to allocate ID segments,
 * making it suitable for single-instance scenarios or testing.
 */
public class AtomicIdSegmentDistributor implements IdSegmentDistributor {
    private static final AtomicInteger ATOMIC_COUNTER = new AtomicInteger();
    private final long step;
    private final String name;
    private final AtomicLong adder = new AtomicLong();

    /**
     * Create a new Atomic distributor with default step size.
     */
    public AtomicIdSegmentDistributor() {
        this(IdSegmentDistributor.DEFAULT_STEP);
    }

    /**
     * Create a new Atomic distributor with custom step size.
     *
     * @param step The step size for ID segments
     */
    public AtomicIdSegmentDistributor(long step) {
        this.step = step;
        this.name = "atomic__" + ATOMIC_COUNTER.incrementAndGet();
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
     * @param step The step size for allocation
     * @return The maximum ID in the allocated range
     */
    @Override
    public long nextMaxId(long step) {
        return adder.addAndGet(step);
    }

}
