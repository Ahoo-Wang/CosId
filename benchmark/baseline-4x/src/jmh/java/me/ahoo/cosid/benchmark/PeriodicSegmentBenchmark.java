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

import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.segment.grouped.DefaultGroupedIdSegmentDistributor;
import me.ahoo.cosid.segment.grouped.GroupBySupplier;
import me.ahoo.cosid.segment.grouped.GroupedId;
import me.ahoo.cosid.segment.grouped.GroupedKey;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * P8: segments grouped by day, crossing day boundaries; throughput and sampled latency (p999 includes the crossings).
 * A day lasts {@link #DAY_MILLIS} of real time on both sides, keyed {@code yyyyMMdd} like the 4.x
 * {@code YearMonthDayGroupBySupplier}, so every run crosses a few boundaries; step {@value #STEP} on both sides.
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class PeriodicSegmentBenchmark {
    static final long STEP = 100;
    static final long DAY_MILLIS = 2000;

    SegmentChainId segment;

    @Setup
    public void setup() {
        DefaultGroupedIdSegmentDistributor distributor = new DefaultGroupedIdSegmentDistributor(new AcceleratedDay(DAY_MILLIS),
            new IdSegmentDistributorDefinition("bench", "p8", 0, STEP), definition -> new Baseline4x.MemoryDistributor(definition.getName(), definition.getStep()));
        segment = new SegmentChainId(distributor);
    }

    @TearDown
    public void tearDown() {
        segment.close();
    }

    @Benchmark
    @Threads(1)
    public GroupedId generate_t1() {
        return segment.generateGrouped();
    }

    @Benchmark
    @Threads(4)
    public GroupedId generate_t4() {
        return segment.generateGrouped();
    }

    /**
     * {@code YearMonthDayGroupBySupplier} with days of {@code dayMillis} real time from a whole second; the group ends
     * at the last second of the day, as {@code LocalTime.MAX} does.
     */
    static final class AcceleratedDay implements GroupBySupplier {
        private static final LocalDate FIRST = LocalDate.of(2026, 10, 26);
        private final long dayMillis;
        private final long origin = (System.currentTimeMillis() / 1000 + 1) * 1000;

        AcceleratedDay(long dayMillis) {
            this.dayMillis = dayMillis;
        }

        @Override
        public GroupedKey get() {
            long day = Math.floorDiv(System.currentTimeMillis() - origin, dayMillis);
            long end = origin + (day + 1) * dayMillis;
            return new GroupedKey(FIRST.plusDays(day).format(DateTimeFormatter.BASIC_ISO_DATE), (end - 1) / 1000);
        }
    }
}
