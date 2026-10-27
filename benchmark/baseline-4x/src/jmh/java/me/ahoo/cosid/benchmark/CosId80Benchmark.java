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

import me.ahoo.cosid.cosid.ClockSyncCosIdGenerator;
import me.ahoo.cosid.cosid.CosIdGenerator;
import me.ahoo.cosid.cosid.FriendlyCosIdGenerator;
import me.ahoo.cosid.cosid.LeasedCosIdGenerator;
import me.ahoo.cosid.cosid.Radix62CosIdGenerator;

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
 * P4: the 80-bit CosId as Radix62 and as Friendly text.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class CosId80Benchmark {
    CosIdGenerator radix62;
    CosIdGenerator friendly;

    @Setup
    public void setup() {
        radix62 = new ClockSyncCosIdGenerator(new LeasedCosIdGenerator(new Radix62CosIdGenerator(Baseline4x.MACHINE_ID), Baseline4x.LEASE));
        friendly = new ClockSyncCosIdGenerator(new LeasedCosIdGenerator(
            new FriendlyCosIdGenerator(Baseline4x.MACHINE_ID, Baseline4x.ZONE, true), Baseline4x.LEASE));
    }

    @Benchmark
    @Threads(1)
    public String radix62_t1() {
        return radix62.generateAsString();
    }

    @Benchmark
    @Threads(1)
    public String friendly_t1() {
        return friendly.generateAsString();
    }
}
