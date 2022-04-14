package me.ahoo.cosid.spring.boot.starter.snowflake;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.snowflake.DefaultClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.spring.boot.starter.IdConverterDefinition;
import me.ahoo.cosid.util.MockIdGenerator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * SnowflakeIdPropertiesTest .
 *
 * @author ahoo wang
 */
class SnowflakeIdPropertiesTest {
    
    @Test
    void isEnabled() {
        SnowflakeIdProperties properties = new SnowflakeIdProperties();
        Assertions.assertFalse(properties.isEnabled());
    }
    
    @Test
    void setEnabled() {
        SnowflakeIdProperties properties = new SnowflakeIdProperties();
        properties.setEnabled(true);
        Assertions.assertTrue(properties.isEnabled());
    }
    
    @Test
    void getZoneId() {
        SnowflakeIdProperties properties = new SnowflakeIdProperties();
        Assertions.assertEquals(ZoneId.systemDefault().getId(), properties.getZoneId());
    }
    
    @Test
    void setZoneId() {
        String[] zoneIds = ZoneId.getAvailableZoneIds().toArray(new String[0]);
        int randomIdx = ThreadLocalRandom.current().nextInt(0, zoneIds.length);
        String zoneId = zoneIds[randomIdx];
        SnowflakeIdProperties properties = new SnowflakeIdProperties();
        properties.setZoneId(zoneId);
        Assertions.assertEquals(zoneId, properties.getZoneId());
    }
    
    @Test
    void getEpoch() {
        SnowflakeIdProperties properties = new SnowflakeIdProperties();
        Assertions.assertEquals(CosId.COSID_EPOCH, properties.getEpoch());
    }
    
    @Test
    void setEpoch() {
        long epoch = System.currentTimeMillis();
        SnowflakeIdProperties properties = new SnowflakeIdProperties();
        properties.setEpoch(epoch);
        Assertions.assertEquals(epoch, properties.getEpoch());
    }
    
    @Test
    void getMachine() {
        SnowflakeIdProperties properties = new SnowflakeIdProperties();
        Assertions.assertNotNull(properties.getMachine());
    }
    
    @Test
    void setMachine() {
        SnowflakeIdProperties.Machine machine = new SnowflakeIdProperties.Machine();
        SnowflakeIdProperties properties = new SnowflakeIdProperties();
        properties.setMachine(machine);
        Assertions.assertEquals(machine, properties.getMachine());
    }
    
    @Test
    void getClockBackwards() {
        SnowflakeIdProperties properties = new SnowflakeIdProperties();
        Assertions.assertNotNull(properties.getClockBackwards());
    }
    
    @Test
    void setClockBackwards() {
        SnowflakeIdProperties.ClockBackwards clockBackwards = new SnowflakeIdProperties.ClockBackwards();
        SnowflakeIdProperties properties = new SnowflakeIdProperties();
        properties.setClockBackwards(clockBackwards);
        Assertions.assertEquals(clockBackwards, properties.getClockBackwards());
    }
    
    @Test
    void getShare() {
        SnowflakeIdProperties properties = new SnowflakeIdProperties();
        Assertions.assertNotNull(properties.getShare());
    }
    
    @Test
    void setShare() {
        SnowflakeIdProperties.IdDefinition idDefinition = new SnowflakeIdProperties.IdDefinition();
        SnowflakeIdProperties properties = new SnowflakeIdProperties();
        properties.setShare(idDefinition);
        Assertions.assertEquals(idDefinition, properties.getShare());
    }
    
    @Test
    void getProvider() {
        SnowflakeIdProperties properties = new SnowflakeIdProperties();
        Assertions.assertNotNull(properties.getProvider());
    }
    
    @Test
    void setProvider() {
        Map<String, SnowflakeIdProperties.IdDefinition> provider = new HashMap<>();
        SnowflakeIdProperties properties = new SnowflakeIdProperties();
        properties.setProvider(provider);
        Assertions.assertEquals(provider, properties.getProvider());
    }
    
    public static class MachineTest {
        @Test
        public void getStable() {
            SnowflakeIdProperties.Machine machine = new SnowflakeIdProperties.Machine();
            Assertions.assertNull(machine.getStable());
        }
        
        @Test
        public void setStable() {
            boolean stable = true;
            SnowflakeIdProperties.Machine machine = new SnowflakeIdProperties.Machine();
            machine.setStable(stable);
            Assertions.assertTrue(machine.getStable());
        }
        
        @Test
        public void getPort() {
            SnowflakeIdProperties.Machine machine = new SnowflakeIdProperties.Machine();
            Assertions.assertNull(machine.getPort());
        }
        
        @Test
        public void setPort() {
            int port = ThreadLocalRandom.current().nextInt();
            SnowflakeIdProperties.Machine machine = new SnowflakeIdProperties.Machine();
            machine.setPort(port);
            Assertions.assertEquals(port, machine.getPort());
        }
        
        @Test
        public void getInstanceId() {
            SnowflakeIdProperties.Machine machine = new SnowflakeIdProperties.Machine();
            Assertions.assertNull(machine.getInstanceId());
        }
        
        @Test
        public void setInstanceId() {
            String instanceId = MockIdGenerator.INSTANCE.generateAsString();
            SnowflakeIdProperties.Machine machine = new SnowflakeIdProperties.Machine();
            machine.setInstanceId(instanceId);
            Assertions.assertEquals(instanceId, machine.getInstanceId());
        }
        
        @Test
        public void getMachineBit() {
            SnowflakeIdProperties.Machine machine = new SnowflakeIdProperties.Machine();
            Assertions.assertEquals(MillisecondSnowflakeId.DEFAULT_MACHINE_BIT, machine.getMachineBit());
        }
        
        @Test
        public void setMachineBit() {
            int machineBit = 9;
            SnowflakeIdProperties.Machine machine = new SnowflakeIdProperties.Machine();
            machine.setMachineBit(machineBit);
            Assertions.assertEquals(machineBit, machine.getMachineBit());
        }
        
        @Test
        public void getStateStorage() {
            SnowflakeIdProperties.Machine machine = new SnowflakeIdProperties.Machine();
            Assertions.assertNotNull(machine.getStateStorage());
        }
        
        @Test
        public void setStateStorage() {
            SnowflakeIdProperties.Machine.StateStorage stateStorage = new SnowflakeIdProperties.Machine.StateStorage();
            SnowflakeIdProperties.Machine machine = new SnowflakeIdProperties.Machine();
            machine.setStateStorage(stateStorage);
            Assertions.assertEquals(stateStorage, machine.getStateStorage());
        }
        
        @Test
        public void getDistributor() {
            SnowflakeIdProperties.Machine machine = new SnowflakeIdProperties.Machine();
            Assertions.assertNotNull(machine.getDistributor());
        }
        
        @Test
        public void setDistributor() {
            SnowflakeIdProperties.Machine.Distributor distributor = new SnowflakeIdProperties.Machine.Distributor();
            SnowflakeIdProperties.Machine machine = new SnowflakeIdProperties.Machine();
            machine.setDistributor(distributor);
            Assertions.assertEquals(distributor, machine.getDistributor());
        }
        
    }
    
    public static class ClockBackwardsTest {
        @Test
        public void getSpinThreshold() {
            SnowflakeIdProperties.ClockBackwards clockBackwards = new SnowflakeIdProperties.ClockBackwards();
            Assertions.assertEquals(DefaultClockBackwardsSynchronizer.DEFAULT_SPIN_THRESHOLD, clockBackwards.getSpinThreshold());
        }
        
        @Test
        public void setSpinThreshold() {
            int spinThreshold = 100;
            SnowflakeIdProperties.ClockBackwards clockBackwards = new SnowflakeIdProperties.ClockBackwards();
            clockBackwards.setSpinThreshold(spinThreshold);
            Assertions.assertEquals(spinThreshold, clockBackwards.getSpinThreshold());
        }
        
        @Test
        public void getBrokenThreshold() {
            SnowflakeIdProperties.ClockBackwards clockBackwards = new SnowflakeIdProperties.ClockBackwards();
            Assertions.assertEquals(DefaultClockBackwardsSynchronizer.DEFAULT_BROKEN_THRESHOLD, clockBackwards.getBrokenThreshold());
        }
        
        @Test
        public void setBrokenThreshold() {
            int brokenThreshold = 10;
            SnowflakeIdProperties.ClockBackwards clockBackwards = new SnowflakeIdProperties.ClockBackwards();
            clockBackwards.setBrokenThreshold(brokenThreshold);
            Assertions.assertEquals(brokenThreshold, clockBackwards.getBrokenThreshold());
        }
    }
    
    static class IdDefinitionTest {
        @Test
        public void isClockSync() {
            SnowflakeIdProperties.IdDefinition idDefinition = new SnowflakeIdProperties.IdDefinition();
            Assertions.assertTrue(idDefinition.isClockSync());
        }
        
        @Test
        public void setClockSync() {
            SnowflakeIdProperties.IdDefinition idDefinition = new SnowflakeIdProperties.IdDefinition();
            idDefinition.setClockSync(false);
            Assertions.assertFalse(idDefinition.isClockSync());
        }
        
        @Test
        public void isFriendly() {
            SnowflakeIdProperties.IdDefinition idDefinition = new SnowflakeIdProperties.IdDefinition();
            Assertions.assertTrue(idDefinition.isFriendly());
        }
        
        @Test
        public void setFriendly() {
            SnowflakeIdProperties.IdDefinition idDefinition = new SnowflakeIdProperties.IdDefinition();
            idDefinition.setFriendly(false);
            Assertions.assertFalse(idDefinition.isFriendly());
        }
        
        @Test
        public void getTimestampUnit() {
            SnowflakeIdProperties.IdDefinition idDefinition = new SnowflakeIdProperties.IdDefinition();
            Assertions.assertEquals(SnowflakeIdProperties.IdDefinition.TimestampUnit.MILLISECOND, idDefinition.getTimestampUnit());
        }
        
        @Test
        public void setTimestampUnit() {
            SnowflakeIdProperties.IdDefinition.TimestampUnit timestampUnit = SnowflakeIdProperties.IdDefinition.TimestampUnit.SECOND;
            SnowflakeIdProperties.IdDefinition idDefinition = new SnowflakeIdProperties.IdDefinition();
            idDefinition.setTimestampUnit(timestampUnit);
            Assertions.assertEquals(timestampUnit, idDefinition.getTimestampUnit());
        }
        
        @Test
        public void getEpoch() {
            SnowflakeIdProperties.IdDefinition idDefinition = new SnowflakeIdProperties.IdDefinition();
            Assertions.assertEquals(0, idDefinition.getEpoch());
        }
        
        @Test
        public void setEpoch() {
            long epoch = System.currentTimeMillis();
            SnowflakeIdProperties.IdDefinition idDefinition = new SnowflakeIdProperties.IdDefinition();
            idDefinition.setEpoch(epoch);
            Assertions.assertEquals(epoch, idDefinition.getEpoch());
        }
        
        @Test
        public void getTimestampBit() {
            SnowflakeIdProperties.IdDefinition idDefinition = new SnowflakeIdProperties.IdDefinition();
            Assertions.assertEquals(MillisecondSnowflakeId.DEFAULT_TIMESTAMP_BIT, idDefinition.getTimestampBit());
        }
        
        @Test
        public void setTimestampBit() {
            int timestampBit = 45;
            SnowflakeIdProperties.IdDefinition idDefinition = new SnowflakeIdProperties.IdDefinition();
            idDefinition.setTimestampBit(timestampBit);
            Assertions.assertEquals(timestampBit, idDefinition.getTimestampBit());
        }
        
        @Test
        public void getMachineBit() {
            SnowflakeIdProperties.IdDefinition idDefinition = new SnowflakeIdProperties.IdDefinition();
            Assertions.assertNull(idDefinition.getMachineBit());
        }
        
        @Test
        public void setMachineBit() {
            Integer machineBit = 2;
            SnowflakeIdProperties.IdDefinition idDefinition = new SnowflakeIdProperties.IdDefinition();
            idDefinition.setMachineBit(machineBit);
            Assertions.assertEquals(machineBit, idDefinition.getMachineBit());
        }
        
        @Test
        public void getSequenceBit() {
            SnowflakeIdProperties.IdDefinition idDefinition = new SnowflakeIdProperties.IdDefinition();
            Assertions.assertEquals(MillisecondSnowflakeId.DEFAULT_SEQUENCE_BIT, idDefinition.getSequenceBit());
        }
        
        @Test
        public void setSequenceBit() {
            int sequenceBit = 10;
            SnowflakeIdProperties.IdDefinition idDefinition = new SnowflakeIdProperties.IdDefinition();
            idDefinition.setSequenceBit(sequenceBit);
            Assertions.assertEquals(sequenceBit, idDefinition.getSequenceBit());
        }
        
        @Test
        public void getConverter() {
            SnowflakeIdProperties.IdDefinition idDefinition = new SnowflakeIdProperties.IdDefinition();
            Assertions.assertNull(idDefinition.getConverter());
        }
        
        @Test
        public void setConverter() {
            IdConverterDefinition converter = new IdConverterDefinition();
            SnowflakeIdProperties.IdDefinition idDefinition = new SnowflakeIdProperties.IdDefinition();
            idDefinition.setConverter(converter);
            Assertions.assertEquals(converter, idDefinition.getConverter());
        }
    }
}
