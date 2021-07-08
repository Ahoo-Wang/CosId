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

package me.ahoo.cosid.redis;

import me.ahoo.cosid.redis.state.*;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class RedisChainIdBenchmark {

    @Benchmark
    public long atomicLong_baseline(JdkIdState jdkIdState) {
        return jdkIdState.jdkId.generate();
    }

    @Benchmark
    @Threads(28)
    public long step_1(SegmentChainIdState segmentChainIdState) {
        return segmentChainIdState.segmentId.generate();
    }

    @Benchmark
    public long step_100(SegmentChainId100State segmentChainId100State) {
        return segmentChainId100State.segmentId.generate();
    }

    @Benchmark
    public long step_1000(SegmentChainId1000State segmentChainId1000State) {
        return segmentChainId1000State.segmentId.generate();
    }

}
