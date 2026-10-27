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

import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.converter.Radix36IdConverter;
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.converter.SnowflakeFriendlyIdConverter;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeIdStateParser;

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
    /** A millisecond Snowflake id of mid-2026. */
    static final long ID = 865_000_000_000_000_000L + 4096 + 7;

    IdConverter radix62;
    IdConverter radix36;
    IdConverter friendly;
    String radix62Text;
    String radix36Text;
    String friendlyText;

    @Setup
    public void setup() {
        radix62 = Radix62IdConverter.PAD_START;
        radix36 = Radix36IdConverter.PAD_START;
        friendly = new SnowflakeFriendlyIdConverter(new MillisecondSnowflakeIdStateParser(
            me.ahoo.cosid.CosId.COSID_EPOCH, MillisecondSnowflakeId.DEFAULT_TIMESTAMP_BIT, MillisecondSnowflakeId.DEFAULT_MACHINE_BIT,
            MillisecondSnowflakeId.DEFAULT_SEQUENCE_BIT, Baseline4x.ZONE, true));
        radix62Text = radix62.asString(ID);
        radix36Text = radix36.asString(ID);
        friendlyText = friendly.asString(ID);
    }

    @Benchmark
    @Threads(1)
    public String radix62Encode_t1() {
        return radix62.asString(ID);
    }

    @Benchmark
    @Threads(1)
    public long radix62Decode_t1() {
        return radix62.asLong(radix62Text);
    }

    @Benchmark
    @Threads(1)
    public String radix36Encode_t1() {
        return radix36.asString(ID);
    }

    @Benchmark
    @Threads(1)
    public long radix36Decode_t1() {
        return radix36.asLong(radix36Text);
    }

    @Benchmark
    @Threads(1)
    public String friendlyEncode_t1() {
        return friendly.asString(ID);
    }

    @Benchmark
    @Threads(1)
    public long friendlyDecode_t1() {
        return friendly.asLong(friendlyText);
    }
}
