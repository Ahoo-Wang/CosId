package me.ahoo.cosid.snowflake;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author : Rocher Kong
 */
class ClockSyncSnowflakeIdTest {
    public static final long TEST_MACHINE_ID = 1;
    ClockSyncSnowflakeId clockSyncSnowflakeId;

    @BeforeEach
    void setup() {
        MillisecondSnowflakeId idGen = new MillisecondSnowflakeId(TEST_MACHINE_ID);
        clockSyncSnowflakeId = new ClockSyncSnowflakeId(idGen);
    }

    @Test
    void getEpoch() {
        Assertions.assertNotNull(clockSyncSnowflakeId.getEpoch());
    }

    @Test
    void getTimestampBit() {
        Assertions.assertNotNull(clockSyncSnowflakeId.getTimestampBit());
    }

    @Test
    void getMachineBit() {
        Assertions.assertNotNull(clockSyncSnowflakeId.getMachineBit());
    }

    @Test
    void getSequenceBit() {
        Assertions.assertNotNull(clockSyncSnowflakeId.getSequenceBit());
    }

    @Test
    void isSafeJavascript() {
        Assertions.assertNotNull(clockSyncSnowflakeId.isSafeJavascript());
    }

    @Test
    void getMaxTimestamp() {
        Assertions.assertNotNull(clockSyncSnowflakeId.getMaxTimestamp());
    }

    @Test
    void getMaxMachine() {
        Assertions.assertNotNull(clockSyncSnowflakeId.getMaxMachine());
    }

    @Test
    void getMaxSequence() {
        Assertions.assertNotNull(clockSyncSnowflakeId.getMaxSequence());
    }

    @Test
    void getLastTimestamp() {
        Assertions.assertNotNull(clockSyncSnowflakeId.getLastTimestamp());
    }

    @Test
    void getMachineId() {
        Assertions.assertNotNull(clockSyncSnowflakeId.getMachineId());
    }
}
