package me.ahoo.cosid.spring.boot.starter.segment;

import static me.ahoo.cosid.segment.IdSegment.TIME_TO_LIVE_FOREVER;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * SegmentIdPropertiesTest .
 *
 * @author ahoo wang
 */
class SegmentIdPropertiesTest {
    
    @Test
    void isEnabled() {
        SegmentIdProperties properties = new SegmentIdProperties();
        Assertions.assertFalse(properties.isEnabled());
    }
    
    @Test
    void setEnabled() {
        SegmentIdProperties properties = new SegmentIdProperties();
        properties.setEnabled(true);
        Assertions.assertTrue(properties.isEnabled());
    }
    
    @Test
    void getMode() {
        SegmentIdProperties properties = new SegmentIdProperties();
        Assertions.assertEquals(SegmentIdProperties.Mode.CHAIN, properties.getMode());
    }
    
    @Test
    void setMode() {
        SegmentIdProperties properties = new SegmentIdProperties();
        properties.setMode(SegmentIdProperties.Mode.DEFAULT);
        Assertions.assertEquals(SegmentIdProperties.Mode.DEFAULT, properties.getMode());
    }
    
    @Test
    void getTtl() {
        SegmentIdProperties properties = new SegmentIdProperties();
        Assertions.assertEquals(TIME_TO_LIVE_FOREVER, properties.getTtl());
    }
    
    @Test
    void setTtl() {
        long ttl = 10;
        SegmentIdProperties properties = new SegmentIdProperties();
        properties.setTtl(ttl);
        Assertions.assertEquals(ttl, properties.getTtl());
    }
    
    @Test
    void getDistributor() {
        SegmentIdProperties properties = new SegmentIdProperties();
        Assertions.assertNotNull(properties.getDistributor());
    }
    
    @Test
    void setDistributor() {
    }
    
    @Test
    void getChain() {
        SegmentIdProperties properties = new SegmentIdProperties();
        Assertions.assertNotNull(properties.getChain());
    }
    
    @Test
    void setChain() {
    }
    
    @Test
    void getShare() {
        SegmentIdProperties properties = new SegmentIdProperties();
        Assertions.assertNotNull(properties.getShare());
    }
    
    @Test
    void setShare() {
    
    }
    
    @Test
    void getProvider() {
        SegmentIdProperties properties = new SegmentIdProperties();
        Assertions.assertNotNull(properties.getProvider());
    }
    
    @Test
    void setProvider() {
    }
}
