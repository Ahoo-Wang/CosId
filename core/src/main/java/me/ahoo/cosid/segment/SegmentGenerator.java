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

package me.ahoo.cosid.segment;

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.time.TimeSource;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Issues the values of ranges reserved from a {@link RangeStore} (04-segment.md §2): one atomic increment on the hot
 * path, refills off the lock and single-flight, reservation size adapted to a time budget.
 */
public final class SegmentGenerator implements IdGenerator, AutoCloseable {
    private final String name;
    private final String key;
    private final RangeStore store;
    private final BatchPolicy policy;
    private final Executor refillExecutor;
    private final TimeSource time;
    private final GeneratorListener listener;

    /**
     * A segment generator.
     *
     * @param name           the generator name, for events and errors
     * @param key            the range key
     * @param store          the range store
     * @param policy         the batch policy
     * @param refillExecutor runs the reservations; the runtime's own executor by default
     * @param time           the time source
     * @param listener       the event listener
     */
    public SegmentGenerator(String name, String key, RangeStore store, BatchPolicy policy,
                            Executor refillExecutor, TimeSource time, GeneratorListener listener) {
        this.name = Objects.requireNonNull(name, "name");
        this.key = Objects.requireNonNull(key, "key");
        this.store = Objects.requireNonNull(store, "store");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.refillExecutor = Objects.requireNonNull(refillExecutor, "refillExecutor");
        this.time = Objects.requireNonNull(time, "time");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public String name() {
        return name;
    }

    public String key() {
        return key;
    }

    public BatchPolicy policy() {
        return policy;
    }

    /**
     * Issues the next value.
     *
     * @return the value
     * @throws me.ahoo.cosid.RangeUnavailableException if no range arrives within the reserve timeout
     * @throws IllegalStateException                   if the generator is closed
     */
    @Override
    public long generate() {
        throw new UnsupportedOperationException("R1-B");
    }

    /**
     * Stops issuing, cancels the refill in flight and drops the ready ranges (S4 allows the gap).
     */
    @Override
    public void close() {
        throw new UnsupportedOperationException("R1-B");
    }
}
