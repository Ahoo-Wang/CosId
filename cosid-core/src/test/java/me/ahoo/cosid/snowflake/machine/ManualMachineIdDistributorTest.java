package me.ahoo.cosid.snowflake.machine;

import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ManualMachineIdDistributorTest .
 *
 * @author ahoo wang
 */
class ManualMachineIdDistributorTest {
    public static final int TEST_MANUAL_MACHINE_ID = 1;
    ManualMachineIdDistributor machineIdDistributor;
    
    @BeforeEach
    void setup() {
        machineIdDistributor = new ManualMachineIdDistributor(TEST_MANUAL_MACHINE_ID, MachineStateStorage.LOCAL, ClockBackwardsSynchronizer.DEFAULT);
    }
    
    @Test
    void getMachineId() {
        Assertions.assertEquals(TEST_MANUAL_MACHINE_ID, machineIdDistributor.getMachineId());
    }
    
    @Test
    void distribute() {
        Assertions.assertEquals(TEST_MANUAL_MACHINE_ID,
            machineIdDistributor.distribute(MockIdGenerator.INSTANCE.generateAsString(), TEST_MANUAL_MACHINE_ID, InstanceId.NONE, MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION).getMachineId());
    }
    
    @Test
    void revert() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        machineIdDistributor.distribute(namespace, TEST_MANUAL_MACHINE_ID, InstanceId.NONE, MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION);
        machineIdDistributor.revert(namespace, InstanceId.NONE);
    }
    
    @Test
    void revertNone() {
        Assertions.assertThrows(NotFoundMachineStateException.class, () -> {
            machineIdDistributor.revert(MockIdGenerator.INSTANCE.generateAsString(), InstanceId.NONE);
        });
    }
}
