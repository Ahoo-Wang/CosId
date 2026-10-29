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

import me.ahoo.cosid.runtime.CosIdRuntime;
import me.ahoo.cosid.runtime.GeneratorSpec;
import me.ahoo.cosid.runtime.RuntimeOptions;
import me.ahoo.cosid.runtime.RuntimeSpec;
import me.ahoo.cosid.time.UuidV7Generator;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * P13 (benchmark/scenarios.md): UUIDv7 ids (D46) from the generator a {@code CosIdRuntime} hands out, as {@link UUID}s and
 * as text. 5.x only, with no 4.x counterpart: {@code compare} reports it as a trend and never judges it.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class UuidV7Benchmark {
    CosIdRuntime runtime;
    UuidV7Generator uuidV7;

    @Setup
    public void setup() {
        runtime = CosIdRuntime.start(RuntimeSpec.builder("p13").generator("p13", GeneratorSpec.UuidV7.builder().build()).build(),
            RuntimeOptions.defaults());
        uuidV7 = runtime.get("p13", UuidV7Generator.class);
    }

    @TearDown
    public void tearDown() {
        runtime.close();
    }

    @Benchmark
    @Threads(1)
    public UUID generate_t1() {
        return uuidV7.generateUuid();
    }

    @Benchmark
    @Threads(4)
    public UUID generate_t4() {
        return uuidV7.generateUuid();
    }

    @Benchmark
    @Threads(16)
    public UUID generate_t16() {
        return uuidV7.generateUuid();
    }

    @Benchmark
    @Threads(1)
    public String text_t1() {
        return uuidV7.generate();
    }
}
