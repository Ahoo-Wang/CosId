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

package me.ahoo.cosid.benchmark;

import me.ahoo.cosid.lease.SlotHandle;
import me.ahoo.cosid.lease.StaticSlot;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.segment.BatchPolicy;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.segment.SegmentGenerator;
import me.ahoo.cosid.time.ClockRegressionPolicy;
import me.ahoo.cosid.time.Layout;
import me.ahoo.cosid.time.TimeBasedGenerator;
import me.ahoo.cosid.time.TimeSource;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What a 5.x runtime builds by default, shared by the scenarios (benchmark/scenarios.md).
 *
 * <p>TODO(R1): the 5.x generators are skeletons until R1; these scenarios compile but fail at setup until then.
 */
final class V5 {
    static final int SLOT = 1;
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private V5() {
    }

    static SlotHandle slot() {
        StaticSlot slot = new StaticSlot(SLOT, 10, Optional.empty(), Duration.ofSeconds(10), Optional.empty(), EXECUTOR, TimeSource.SYSTEM);
        slot.start();
        return slot;
    }

    static TimeBasedGenerator timeBased(String name, Layout layout) {
        return new TimeBasedGenerator(name, layout, slot(), TimeSource.SYSTEM, ClockRegressionPolicy.DEFAULT, GeneratorListener.NOOP);
    }

    /**
     * {@code BatchPolicy.DEFAULT} with another step and that step's default {@code maxBatch}, the rest unchanged.
     */
    static BatchPolicy batch(long step) {
        BatchPolicy defaults = BatchPolicy.DEFAULT;
        return new BatchPolicy(step, defaults.initialOffset(), defaults.lookahead(), BatchPolicy.defaultMaxBatch(step), defaults.reserveTimeout(), defaults.rangeTtl());
    }

    static SegmentGenerator segment(String name, BatchPolicy policy) {
        return new SegmentGenerator(name, "bench/" + name, new MemoryRangeStore(), policy, EXECUTOR, TimeSource.SYSTEM, GeneratorListener.NOOP);
    }

    /**
     * An in-memory range store with no simulated latency, like the 4.x {@code Baseline4x.MemoryDistributor}: one
     * atomic add per reservation.
     *
     * <p>TODO(R1-C): use {@code store.tck.InMemoryStore} once it is implemented, if it costs the same.
     */
    static final class MemoryRangeStore implements RangeStore {
        private final ConcurrentHashMap<String, AtomicLong> values = new ConcurrentHashMap<>();

        @Override
        public Range reserve(String key, long size, long initialOffset) {
            AtomicLong value = values.get(key);
            if (value == null) {
                value = values.computeIfAbsent(key, k -> new AtomicLong(initialOffset));
            }
            long end = value.addAndGet(size);
            return new Range(end - size, end);
        }
    }
}
