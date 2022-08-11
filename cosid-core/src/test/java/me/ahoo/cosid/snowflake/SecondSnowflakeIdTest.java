package me.ahoo.cosid.snowflake;

import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.test.ConcurrentGenerateSpec;
import me.ahoo.cosid.test.ConcurrentGenerateStingSpec;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * SecondSnowflakeIdTest .
 *
 * @author ahoo wang
 */
class SecondSnowflakeIdTest {
    public static final long TEST_MACHINE_ID = 1;
    SnowflakeFriendlyId snowflakeId;
    
    @BeforeEach
    void setup() {
        SecondSnowflakeId idGen = new SecondSnowflakeId(TEST_MACHINE_ID);
        snowflakeId = new DefaultSnowflakeFriendlyId(new ClockSyncSnowflakeId(idGen));
    }
    
    @Test
    public void generate() {
        long idFirst = snowflakeId.generate();
        long idSecond = snowflakeId.generate();
        Assertions.assertTrue(idSecond > idFirst);
        
        SnowflakeIdState idState = snowflakeId.getParser().parse(idFirst);
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
    
    @Test
    public void safeJavaScript() {
        SnowflakeId snowflakeId = SafeJavaScriptSnowflakeId.ofSecond(1);
        Assertions.assertTrue(snowflakeId.isSafeJavascript());
    }
    
    @Test
    public void customizeEpoch() {
        
        SnowflakeId idGen = new SecondSnowflakeId(LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond(),
            SecondSnowflakeId.DEFAULT_TIMESTAMP_BIT,
            SecondSnowflakeId.DEFAULT_MACHINE_BIT,
            SecondSnowflakeId.DEFAULT_SEQUENCE_BIT, 1023, 512);
        SecondSnowflakeIdStateParser snowflakeIdStateParser = SecondSnowflakeIdStateParser.of(idGen);
        long idFirst = idGen.generate();
        long idSecond = idGen.generate();
        
        Assertions.assertTrue(idSecond > idFirst);
        
        SnowflakeIdState idState = snowflakeIdStateParser.parse(idFirst);
        Assertions.assertEquals(idState.getTimestamp().toLocalDate(), LocalDate.now());
        SnowflakeIdState idStateOfFriendlyId = snowflakeIdStateParser.parse(idState.getFriendlyId());
        Assertions.assertEquals(idState, idStateOfFriendlyId);
    }
    
    @Test
    public void generateWhenConcurrent() {
        new ConcurrentGenerateSpec(snowflakeId) {
            @Override
            protected void assertGlobalFirst(long id) {
            }
            
            @Override
            protected void assertGlobalEach(long previousId, long id) {
                Assertions.assertTrue(id > previousId);
            }
            
            @Override
            protected void assertGlobalLast(long lastId) {
            }
            
        }.verify();
    }
    
    @Test
    public void generateWhenConcurrentString() {
        new ConcurrentGenerateStingSpec(new StringSnowflakeId(snowflakeId, Radix62IdConverter.PAD_START)).verify();
    }
}
