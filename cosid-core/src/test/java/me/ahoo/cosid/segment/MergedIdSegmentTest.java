package me.ahoo.cosid.segment;

import me.ahoo.cosid.segment.grouped.GroupedKey;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author : Rocher Kong
 */
class MergedIdSegmentTest {
    IdSegment delegate;
    MergedIdSegment mergedIdSegment;

    @BeforeEach
    void setUp() {
        delegate = new DefaultIdSegment(10, 10, 123, 456, GroupedKey.NEVER);
        mergedIdSegment = new MergedIdSegment(2, delegate);
    }

    @Test
    void getSegments() {
        Assertions.assertEquals(2, mergedIdSegment.getSegments());
    }

    @Test
    void getSingleStep() {
        Assertions.assertEquals(5, mergedIdSegment.getSingleStep());
    }

    @Test
    void getFetchTime() {
        Assertions.assertEquals(delegate.getFetchTime(), mergedIdSegment.getFetchTime());
    }

    @Test
    void toStringTest() {
        Assertions.assertEquals("MergedIdSegment{segments=2, idSegment=" + delegate + ", singleStep=5}", mergedIdSegment.toString());
    }
}
