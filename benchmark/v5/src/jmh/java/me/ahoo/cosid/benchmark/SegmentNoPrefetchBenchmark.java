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

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.runtime.CosIdRuntime;
import me.ahoo.cosid.segment.BatchPolicy;

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
import java.util.concurrent.TimeUnit;

/**
 * P6: segments with step 1 and no prefetching (the same on both sides).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class SegmentNoPrefetchBenchmark {
    CosIdRuntime runtime;
    IdGenerator segment;

    @Setup
    public void setup() {
        runtime = V5.segmentRuntime("p6", BatchPolicy.builder()
            .step(1)
            .initialOffset(0)
            .lookahead(Duration.ZERO)
            .maxBatch(1)
            .reserveTimeout(Duration.ofSeconds(3))
            .build());
        segment = runtime.generator("p6");
    }

    @TearDown
    public void tearDown() {
        runtime.close();
    }

    @Benchmark
    @Threads(1)
    public long generate_t1() {
        return segment.generate();
    }
}
