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

import me.ahoo.cosid.lease.SlotBinding;
import me.ahoo.cosid.lease.SlotHandle;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.segment.BatchPolicy;
import me.ahoo.cosid.segment.SegmentGenerator;
import me.ahoo.cosid.store.tck.InMemoryStore;
import me.ahoo.cosid.time.ClockRegressionPolicy;
import me.ahoo.cosid.time.Layout;
import me.ahoo.cosid.time.TimeBasedGenerator;
import me.ahoo.cosid.time.TimeSource;

import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * What a 5.x runtime builds by default, shared by the scenarios (benchmark/scenarios.md).
 *
 * <p>TODO(R1-B): the segment scenarios fail at setup until R1-B.
 */
final class V5 {
    static final int SLOT = 1;
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private V5() {
    }

    /**
     * A fixed binding with an unbounded window, as 4.x {@code MachineIdLease.FOREVER} and a {@code StaticSlot} without a
     * file. The generator still reads it and checks the window on every id.
     *
     * <p>TODO(R1-C): back to {@code new StaticSlot(SLOT, 10, Optional.empty(), Duration.ofSeconds(10), Optional.empty(),
     * EXECUTOR, TimeSource.SYSTEM)} once {@code StaticSlot} is implemented.
     */
    static SlotHandle slot() {
        SlotBinding binding = new SlotBinding(SLOT, Long.MIN_VALUE, Long.MAX_VALUE, 0);
        return () -> binding;
    }

    static TimeBasedGenerator timeBased(String name, Layout layout) {
        return new TimeBasedGenerator(name, layout, slot(), TimeSource.SYSTEM, ClockRegressionPolicy.DEFAULT, GeneratorListener.NOOP);
    }

    /**
     * {@code BatchPolicy.DEFAULT} with another step: {@code maxBatch = step * 1000}, the rest unchanged.
     */
    static BatchPolicy batch(long step) {
        BatchPolicy defaults = BatchPolicy.DEFAULT;
        return new BatchPolicy(step, defaults.initialOffset(), defaults.lookahead(), step * 1000, defaults.reserveTimeout(), defaults.rangeTtl());
    }

    static SegmentGenerator segment(String name, BatchPolicy policy) {
        return new SegmentGenerator(name, "bench/" + name, new InMemoryStore(), policy, EXECUTOR, TimeSource.SYSTEM, GeneratorListener.NOOP);
    }
}
