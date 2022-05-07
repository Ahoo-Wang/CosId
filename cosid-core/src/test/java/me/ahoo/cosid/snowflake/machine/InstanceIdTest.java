package me.ahoo.cosid.snowflake.machine;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * InstanceIdTest .
 *
 * @author ahoo wang
 */
class InstanceIdTest {
    InstanceId instanceId1 = InstanceId.of("localhost", 8080, true);
    InstanceId instanceId2 = InstanceId.of("localhost", 8080, true);
    
    @Test
    void testEquals() {
        Assertions.assertEquals(instanceId1, instanceId2);
    }
    
    @Test
    void testHashCode() {
        Assertions.assertEquals(instanceId1.hashCode(), instanceId2.hashCode());
    }
}
