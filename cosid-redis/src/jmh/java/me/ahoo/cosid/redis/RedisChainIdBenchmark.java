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
