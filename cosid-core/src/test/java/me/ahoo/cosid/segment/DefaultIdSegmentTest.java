package me.ahoo.cosid.segment;

import me.ahoo.cosid.test.ConcurrentGenerateSpec;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * DefaultIdSegmentTest .
 *
 * @author ahoo wang
 */
class DefaultIdSegmentTest {
    
    @Test
    void incrementAndGet() {
        DefaultIdSegment segment = new DefaultIdSegment(1, 1);
        long id = segment.incrementAndGet();
        Assertions.assertEquals(1, id);
    }
    
    @Test
    void incrementAndGetWhenOverflow() {
        DefaultIdSegment segment = new DefaultIdSegment(1, 1);
        segment.incrementAndGet();
        Assertions.assertEquals(-1, segment.incrementAndGet());
    }
    
    @Test
    void incrementAndGetWhenConcurrent() {
        final DefaultIdSegment segment = new DefaultIdSegment(Long.MAX_VALUE, Long.MAX_VALUE);
        new ConcurrentGenerateSpec(segment::incrementAndGet).verify();
    }
}
