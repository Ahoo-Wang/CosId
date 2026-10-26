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

import me.ahoo.cosid.StringIdGenerator;
import me.ahoo.cosid.codec.Friendly;
import me.ahoo.cosid.codec.Radix;
import me.ahoo.cosid.time.Layout;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;

import java.util.concurrent.TimeUnit;

/**
 * P3: millisecond Snowflake as Radix62 and as Friendly text.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class SnowflakeStringBenchmark {
    StringIdGenerator radix62;
    StringIdGenerator friendly;

    @Setup
    public void setup() {
        radix62 = new StringIdGenerator(V5.timeBased("p3-radix62", Layout.SNOWFLAKE_MS), Radix.of(62, 11, true));
        friendly = new StringIdGenerator(V5.timeBased("p3-friendly", Layout.SNOWFLAKE_MS), Friendly.of(Layout.SNOWFLAKE_MS, V5.ZONE, true));
    }

    @Benchmark
    @Threads(1)
    public String radix62_t1() {
        return radix62.generate();
    }

    @Benchmark
    @Threads(1)
    public String friendly_t1() {
        return friendly.generate();
    }
}
