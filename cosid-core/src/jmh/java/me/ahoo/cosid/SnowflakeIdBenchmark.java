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

package me.ahoo.cosid;

import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.ClockSyncSnowflakeId;
import me.ahoo.cosid.snowflake.DefaultSnowflakeFriendlyId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.SafeJavaScriptSnowflakeId;
import me.ahoo.cosid.snowflake.SecondSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeFriendlyId;
import me.ahoo.cosid.snowflake.SnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeIdState;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class SnowflakeIdBenchmark {
    SnowflakeId millisecondSnowflakeId;
    SnowflakeId secondSnowflakeId;
    SnowflakeId safeJsMillisecondSnowflakeId;
    SnowflakeId safeJsSecondSnowflakeId;
    SnowflakeFriendlyId snowflakeFriendlyId;

    @Setup
    public void setup() {
        millisecondSnowflakeId = new ClockSyncSnowflakeId(new MillisecondSnowflakeId(1));
        secondSnowflakeId = new ClockSyncSnowflakeId(new SecondSnowflakeId(1));
        safeJsSecondSnowflakeId = new ClockSyncSnowflakeId(SafeJavaScriptSnowflakeId.ofSecond(1));
        safeJsMillisecondSnowflakeId = new ClockSyncSnowflakeId(SafeJavaScriptSnowflakeId.ofMillisecond(1));
        snowflakeFriendlyId = new DefaultSnowflakeFriendlyId(new ClockSyncSnowflakeId(new MillisecondSnowflakeId(1), ClockBackwardsSynchronizer.DEFAULT));
    }

    @Benchmark
    public long millisecondSnowflakeId_generate() {
        return millisecondSnowflakeId.generate();
    }

    @Benchmark
    public SnowflakeIdState millisecondSnowflakeId_friendlyId() {
        return snowflakeFriendlyId.friendlyId();
    }

    @Benchmark
    public long secondSnowflakeId_generate() {
        return secondSnowflakeId.generate();
    }

    @Benchmark
    public long safeJsMillisecondSnowflakeId_generate() {
        return safeJsMillisecondSnowflakeId.generate();
    }

    @Benchmark
    public long safeJsSecondSnowflakeId_generate() {
        return safeJsSecondSnowflakeId.generate();
    }
}
