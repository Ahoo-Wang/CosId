package me.ahoo.cosid.segment;

import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static me.ahoo.cosid.segment.IdSegment.TIME_TO_LIVE_FOREVER;

/**
 * @author : Rocher Kong
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MergedIdSegmentTest {
    MergedIdSegment mergedIdSegment;
    @BeforeAll
    void setUp(){
        SegmentChainId segmentChainId = new SegmentChainId(TIME_TO_LIVE_FOREVER, 10, new IdSegmentDistributor.Atomic(2), PrefetchWorkerExecutorService.DEFAULT);
        mergedIdSegment=new MergedIdSegment(2,segmentChainId.getHead());
    }

    @Test
    void getSegments() {
        Assertions.assertEquals(2,mergedIdSegment.getSegments());
    }

    @Test
    void getSingleStep() {
        Assertions.assertEquals(0,mergedIdSegment.getSingleStep());
    }

    @Test
    void getFetchTime() {
        Assertions.assertNotNull(mergedIdSegment.getFetchTime());
    }

    @Test
    void toStringTest(){
        Assertions.assertNotNull(mergedIdSegment.toString());
    }
}
