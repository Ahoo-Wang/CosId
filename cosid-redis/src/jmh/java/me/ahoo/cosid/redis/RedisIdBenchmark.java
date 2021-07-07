package me.ahoo.cosid.redis;

import me.ahoo.cosid.redis.state.SegmentId1000State;
import me.ahoo.cosid.redis.state.SegmentId100State;
import me.ahoo.cosid.redis.state.SegmentIdState;
import org.openjdk.jmh.annotations.*;

/**
 * @author ahoo wang
 */
public class RedisIdBenchmark {

    @Benchmark
    @Threads(28)
    public long step_1(SegmentIdState segmentIdState) {
        return segmentIdState.segmentId.generate();
    }

    @Benchmark
    public long step_100(SegmentId100State segmentId100State) {
        return segmentId100State.segmentId.generate();
    }

    @Benchmark
    public long step_1000(SegmentId1000State segmentId1000State) {
        return segmentId1000State.segmentId.generate();
    }

}
