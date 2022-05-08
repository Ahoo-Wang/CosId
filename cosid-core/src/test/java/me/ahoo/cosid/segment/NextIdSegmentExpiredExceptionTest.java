package me.ahoo.cosid.segment;

import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import static me.ahoo.cosid.segment.IdSegment.TIME_TO_LIVE_FOREVER;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author : Rocher Kong
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NextIdSegmentExpiredExceptionTest {
    SegmentChainId segmentChainIdCurrent;
    SegmentChainId segmentChainIdNext ;
    NextIdSegmentExpiredException nextIdSegmentExpiredException;


    @BeforeEach
    void setUp(){
         segmentChainIdCurrent = new SegmentChainId(TIME_TO_LIVE_FOREVER, 10, new IdSegmentDistributor.Atomic(2), PrefetchWorkerExecutorService.DEFAULT);
         segmentChainIdNext = new SegmentChainId(TIME_TO_LIVE_FOREVER, 10, new IdSegmentDistributor.Atomic(2), PrefetchWorkerExecutorService.DEFAULT);
         nextIdSegmentExpiredException = new NextIdSegmentExpiredException(segmentChainIdCurrent.getHead(), segmentChainIdNext.getHead());
    }

    @Test
    void getCurrent() {
        Assertions.assertEquals(segmentChainIdCurrent.getHead(), nextIdSegmentExpiredException.getCurrent());
    }

    @Test
    void getNext() {
        Assertions.assertEquals(segmentChainIdNext.getHead(), nextIdSegmentExpiredException.getNext());
    }
}
