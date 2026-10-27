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

import me.ahoo.cosid.GroupedId;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.segment.Period;
import me.ahoo.cosid.segment.PeriodicPolicy;
import me.ahoo.cosid.segment.PeriodicSegmentGenerator;
import me.ahoo.cosid.time.TimeSource;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * P8: segments grouped by day, crossing day boundaries; throughput and sampled latency (p999 includes the crossings).
 * A day lasts {@link #DAY_MILLIS} of real time on both sides, keyed {@code yyyyMMdd} like {@code YEAR_MONTH_DAY}, so
 * every run crosses a few boundaries; step {@value #STEP} on both sides.
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class PeriodicSegmentBenchmark {
    static final long STEP = 100;
    static final long DAY_MILLIS = 2000;

    PeriodicSegmentGenerator segment;

    @Setup
    public void setup() {
        // The prewarm is the default minute scaled to the day: 1/1440 of it.
        PeriodicPolicy periodic = new PeriodicPolicy(Duration.ofMillis(Math.max(1, DAY_MILLIS * 60 / 86_400)), Optional.empty());
        segment = new PeriodicSegmentGenerator("p8", "bench/p8", new AcceleratedDay(DAY_MILLIS), V5.ZONE, new V5.MemoryRangeStore(),
            V5.batch(STEP), periodic, V5.EXECUTOR, TimeSource.SYSTEM, GeneratorListener.NOOP);
    }

    @TearDown
    public void tearDown() {
        segment.close();
    }

    @Benchmark
    @Threads(1)
    public GroupedId generate_t1() {
        return segment.generate();
    }

    @Benchmark
    @Threads(4)
    public GroupedId generate_t4() {
        return segment.generate();
    }

    /**
     * {@code YEAR_MONTH_DAY} with days of {@code dayMillis} real time from a whole second.
     */
    static final class AcceleratedDay implements Period {
        private static final LocalDate FIRST = LocalDate.of(2026, 10, 26);
        private final long dayMillis;
        private final long origin = (System.currentTimeMillis() / 1000 + 1) * 1000;

        AcceleratedDay(long dayMillis) {
            this.dayMillis = dayMillis;
        }

        @Override
        public String keyOf(Instant instant, ZoneId zone) {
            return FIRST.plusDays(Math.floorDiv(instant.toEpochMilli() - origin, dayMillis)).format(DateTimeFormatter.BASIC_ISO_DATE);
        }

        @Override
        public Instant endOf(String key, ZoneId zone) {
            long day = ChronoUnit.DAYS.between(FIRST, LocalDate.parse(key, DateTimeFormatter.BASIC_ISO_DATE));
            return Instant.ofEpochMilli(origin + (day + 1) * dayMillis);
        }
    }
}
