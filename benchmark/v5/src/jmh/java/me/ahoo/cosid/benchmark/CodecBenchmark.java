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

import me.ahoo.cosid.codec.Friendly;
import me.ahoo.cosid.codec.IdCodec;
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
 * P9: Radix62 / Radix36 encode and decode, Friendly encode and parse, of one millisecond Snowflake id.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class CodecBenchmark {
    /** A millisecond Snowflake id of mid-2026, the same as the 4.x baseline. */
    static final long ID = 865_000_000_000_000_000L + 4096 + 7;

    IdCodec radix62;
    IdCodec radix36;
    IdCodec friendly;
    String radix62Text;
    String radix36Text;
    String friendlyText;

    @Setup
    public void setup() {
        radix62 = Radix.of(62, 11, true);
        radix36 = Radix.of(36, 13, true);
        friendly = Friendly.of(Layout.SNOWFLAKE_MS, V5.ZONE, true);
        radix62Text = radix62.encode(ID);
        radix36Text = radix36.encode(ID);
        friendlyText = friendly.encode(ID);
    }

    @Benchmark
    @Threads(1)
    public String radix62Encode_t1() {
        return radix62.encode(ID);
    }

    @Benchmark
    @Threads(1)
    public long radix62Decode_t1() {
        return radix62.decode(radix62Text);
    }

    @Benchmark
    @Threads(1)
    public String radix36Encode_t1() {
        return radix36.encode(ID);
    }

    @Benchmark
    @Threads(1)
    public long radix36Decode_t1() {
        return radix36.decode(radix36Text);
    }

    @Benchmark
    @Threads(1)
    public String friendlyEncode_t1() {
        return friendly.encode(ID);
    }

    @Benchmark
    @Threads(1)
    public long friendlyDecode_t1() {
        return friendly.decode(friendlyText);
    }
}
