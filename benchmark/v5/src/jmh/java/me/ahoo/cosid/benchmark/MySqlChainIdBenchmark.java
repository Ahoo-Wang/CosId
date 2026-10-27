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

import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.segment.SegmentGenerator;
import me.ahoo.cosid.store.jdbc.JdbcStore;
import me.ahoo.cosid.time.TimeSource;

import com.zaxxer.hikari.HikariDataSource;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * P7: segments on a real MySQL ({@link MySql}), 1 / 4 / 16 threads, step 10 and 100; throughput and sampled latency.
 * {@code SegmentGenerator} on the JDBC store, {@code BatchPolicy.DEFAULT} with {@code step}, a new range key per trial.
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class MySqlChainIdBenchmark {
    /** The reservation step, the same on both sides. */
    @Param({"10", "100"})
    long step;

    HikariDataSource dataSource;
    SegmentGenerator segment;

    @Setup
    public void setup() {
        dataSource = MySql.dataSource();
        JdbcStore store = JdbcStore.of(dataSource, Duration.ofSeconds(3));
        store.initSchema();
        segment = new SegmentGenerator("p7", "bench/p7-" + UUID.randomUUID(), store.ranges(), V5.batch(step), V5.EXECUTOR, TimeSource.SYSTEM,
            GeneratorListener.NOOP);
    }

    @TearDown
    public void tearDown() {
        segment.close();
        dataSource.close();
    }

    @Benchmark
    @Threads(1)
    public long generate_t1() {
        return segment.generate();
    }

    @Benchmark
    @Threads(4)
    public long generate_t4() {
        return segment.generate();
    }

    @Benchmark
    @Threads(16)
    public long generate_t16() {
        return segment.generate();
    }
}
