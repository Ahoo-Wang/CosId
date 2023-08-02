package me.ahoo.cosid.segment;

import me.ahoo.cosid.converter.Radix62IdConverter;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * StringSegmentIdTest .
 *
 * @author ahoo wang
 */
class StringSegmentIdTest {
    
    @Test
    void ctor() {
        DefaultSegmentId delegate = new DefaultSegmentId(new IdSegmentDistributor.Mock());
        StringSegmentId stringSegmentId = new StringSegmentId(delegate, Radix62IdConverter.PAD_START);
        Assertions.assertNotNull(stringSegmentId);
        Assertions.assertNotNull(stringSegmentId.current());
    }
}
