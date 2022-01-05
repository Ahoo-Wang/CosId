/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

import me.ahoo.cosid.jvm.AtomicLongGenerator;
import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.segment.SegmentId;
import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.UUID;

import static me.ahoo.cosid.segment.IdSegment.TIME_TO_LIVE_FOREVER;


/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class SegmentIdBenchmark {

    SegmentId segmentId;
    SegmentChainId segmentChainId;
    AtomicLongGenerator  atomicLongGenerator;

    @Setup
    public void setup() {
        atomicLongGenerator = new AtomicLongGenerator();
        segmentId = new DefaultSegmentId(new IdSegmentDistributor.Mock());
        segmentChainId = new SegmentChainId(TIME_TO_LIVE_FOREVER, 10, new IdSegmentDistributor.Mock(), PrefetchWorkerExecutorService.DEFAULT);
    }

    @Benchmark
    public UUID uuid_generate() {
        return UUID.randomUUID();
    }

    @Benchmark
    public long atomicLong_generate() {
        return atomicLongGenerator.generate();
    }

    @Benchmark
    public long segmentId_generate() {
        return segmentId.generate();
    }

    @Benchmark
    public long segmentChainId_generate() {
        return segmentChainId.generate();
    }

}
