package me.ahoo.cosid;

import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.segment.SegmentId;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;


/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class SegmentIdBenchmark {

    SegmentId segmentId;
    SegmentChainId segmentChainId;

    @Setup
    public void setup() {
        segmentId = new DefaultSegmentId(new IdSegmentDistributor.Mock());
        segmentChainId = new SegmentChainId(10, SegmentChainId.DEFAULT_PREFETCH_PERIOD, new IdSegmentDistributor.Mock());
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
