package me.ahoo.cosid.redis.state;

import me.ahoo.cosid.redis.RedisIdFactory;
import me.ahoo.cosid.segment.SegmentId;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class SegmentChainIdState {
    public  SegmentId segmentId;

    @Setup
    public void setup() {
        segmentId = RedisIdFactory.INSTANCE.createSegmentChainId(1);
    }

    @TearDown
    public void tearDown() {
        RedisIdFactory.INSTANCE.close();
    }
}
