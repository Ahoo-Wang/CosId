package me.ahoo.cosid.snowflake;

import me.ahoo.cosid.converter.Radix62IdConverter;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * StringSnowflakeIdTest .
 *
 * @author ahoo wang
 */
class StringSnowflakeIdTest {
    public static final int TEST_MACHINE_ID = 1;
    SnowflakeId delegate = new MillisecondSnowflakeId(TEST_MACHINE_ID);
    StringSnowflakeId snowflakeId;
    
    @BeforeEach
    void setup() {
        snowflakeId = new StringSnowflakeId(delegate, Radix62IdConverter.INSTANCE);
    }
    
    @Test
    void getEpoch() {
        Assertions.assertEquals(delegate.getEpoch(), snowflakeId.getEpoch());
    }
    
    @Test
    void getTimestampBit() {
        Assertions.assertEquals(delegate.getTimestampBit(), snowflakeId.getTimestampBit());
    }
    
    @Test
    void getMachineBit() {
        Assertions.assertEquals(delegate.getMachineBit(), snowflakeId.getMachineBit());
    }
    
    @Test
    void getSequenceBit() {
        Assertions.assertEquals(delegate.getSequenceBit(), snowflakeId.getSequenceBit());
    }
    
    @Test
    void getMaxTimestamp() {
        Assertions.assertEquals(delegate.getMaxTimestamp(), snowflakeId.getMaxTimestamp());
    }
    
    @Test
    void getMaxMachineId() {
        Assertions.assertEquals(delegate.getMaxMachineId(), snowflakeId.getMaxMachineId());
    }
    
    @Test
    void getMaxSequence() {
        Assertions.assertEquals(delegate.getMaxSequence(), snowflakeId.getMaxSequence());
    }
    
    @Test
    void getLastTimestamp() {
        Assertions.assertEquals(delegate.getLastTimestamp(), snowflakeId.getLastTimestamp());
    }
    
    @Test
    void getMachineId() {
        Assertions.assertEquals(delegate.getMachineId(), snowflakeId.getMachineId());
    }
}
