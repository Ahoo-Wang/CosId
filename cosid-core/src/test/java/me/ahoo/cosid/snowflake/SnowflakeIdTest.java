package me.ahoo.cosid.snowflake;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SnowflakeIdTest .
 *
 * @author ahoo wang
 */
class SnowflakeIdTest {
    static final long TEST_MACHINE_ID = 1;
    SnowflakeFriendlyId snowflakeId;
    
    @BeforeEach
    void setup() {
        MillisecondSnowflakeId idGen = new MillisecondSnowflakeId(TEST_MACHINE_ID);
        snowflakeId = new DefaultSnowflakeFriendlyId(idGen);
    }
    
    @Test
    public void generate() {
        long idFirst = snowflakeId.generate();
        long idSecond = snowflakeId.generate();
        Assertions.assertTrue(idSecond > idFirst);
        
        MillisecondSnowflakeIdStateParser snowflakeIdStateParser = MillisecondSnowflakeIdStateParser.of(snowflakeId);
        SnowflakeIdState idState = snowflakeIdStateParser.parse(idFirst);
        Assertions.assertNotNull(idState);
        Assertions.assertEquals(TEST_MACHINE_ID, idState.getMachineId());
    }
    
    @Test
    public void friendlyId() {
        long id = snowflakeId.generate();
        SnowflakeIdState snowflakeIdState = snowflakeId.friendlyId(id);
        Assertions.assertNotNull(snowflakeIdState);
        Assertions.assertEquals(TEST_MACHINE_ID, snowflakeIdState.getMachineId());
        Assertions.assertEquals(id, snowflakeIdState.getId());
        SnowflakeIdState snowflakeIdState2 = snowflakeId.ofFriendlyId(snowflakeIdState.getFriendlyId());
        Assertions.assertEquals(snowflakeIdState2, snowflakeIdState);
    }
}
