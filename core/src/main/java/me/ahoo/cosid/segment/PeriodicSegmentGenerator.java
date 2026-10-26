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

import me.ahoo.cosid.GroupedId;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.time.TimeSource;

import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Segments grouped by period (D5, 04-segment.md §3): at most two {@link SegmentGenerator}s, the current period's and
 * the next one's, at the store keys {@code {key}/{period}}.
 */
public final class PeriodicSegmentGenerator implements AutoCloseable {
    private final String name;
    private final String key;
    private final Period period;
    private final ZoneId zone;
    private final RangeStore store;
    private final BatchPolicy policy;
    private final PeriodicPolicy periodic;
    private final Executor refillExecutor;
    private final TimeSource time;
    private final GeneratorListener listener;

    /**
     * A periodic segment generator.
     *
     * @param name           the generator name, for events and errors
     * @param key            the range key the period keys are appended to
     * @param period         the period
     * @param zone           the time zone the periods follow
     * @param store          the range store
     * @param policy         the batch policy of each period
     * @param periodic       the periodic policy
     * @param refillExecutor runs the reservations
     * @param time           the time source
     * @param listener       the event listener
     */
    public PeriodicSegmentGenerator(String name, String key, Period period, ZoneId zone, RangeStore store,
                                    BatchPolicy policy, PeriodicPolicy periodic, Executor refillExecutor,
                                    TimeSource time, GeneratorListener listener) {
        this.name = Objects.requireNonNull(name, "name");
        this.key = Objects.requireNonNull(key, "key");
        this.period = Objects.requireNonNull(period, "period");
        this.zone = Objects.requireNonNull(zone, "zone");
        this.store = Objects.requireNonNull(store, "store");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.periodic = Objects.requireNonNull(periodic, "periodic");
        this.refillExecutor = Objects.requireNonNull(refillExecutor, "refillExecutor");
        this.time = Objects.requireNonNull(time, "time");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public String name() {
        return name;
    }

    /**
     * Issues the next id of the current period.
     *
     * @return the id with its period
     * @throws me.ahoo.cosid.RangeSealedException if the period's key is already sealed (a lagging clock)
     */
    public GroupedId generate() {
        throw new UnsupportedOperationException("R1-B");
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException("R1-B");
    }
}
