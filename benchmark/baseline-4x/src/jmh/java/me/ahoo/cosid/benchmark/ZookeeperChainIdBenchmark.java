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

import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.util.CallTimeout;
import me.ahoo.cosid.zookeeper.ZookeeperIdSegmentDistributor;

import org.apache.curator.retry.ExponentialBackoffRetry;
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

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * P7 (ZooKeeper): segments on ZooKeeper ({@link ZookeeperBench}: an in-process 3.9 server per trial, or
 * {@code COSID_BENCH_ZOOKEEPER_CONNECT}), 1 / 4 / 16 threads, step 10 and 100; throughput and sampled latency.
 * {@code SegmentChainId} on {@code ZookeeperIdSegmentDistributor} (no call timeout, as the 4.x starter), a new counter
 * per trial.
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ZookeeperChainIdBenchmark {
    /** The reservation step, the same on both sides. */
    @Param({"10", "100"})
    long step;

    ZookeeperBench zookeeper;
    SegmentChainId segment;

    @Setup
    public void setup() throws Exception {
        zookeeper = ZookeeperBench.start();
        segment = new SegmentChainId(new ZookeeperIdSegmentDistributor("p7-" + UUID.randomUUID(), "chain", 0, step, zookeeper.client,
            new ExponentialBackoffRetry(100, 5, 500), CallTimeout.NONE));
    }

    @TearDown
    public void tearDown() throws Exception {
        segment.close();
        zookeeper.close();
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
