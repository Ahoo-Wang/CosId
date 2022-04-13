package me.ahoo.cosid.snowflake.machine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * MachineIdTest .
 *
 * @author ahoo wang
 */
class MachineIdTest {
    
    @Test
    void getMachineId() {
        MachineId machineId = new MachineId(1);
        Assertions.assertEquals(1, machineId.getMachineId());
    }
    
    @Test
    void testEquals() {
        MachineId machineId1 = new MachineId(1);
        MachineId machineId2 = new MachineId(1);
        Assertions.assertEquals(machineId1, machineId2);
    }
    
    @Test
    void testHashCode() {
        MachineId machineId1 = new MachineId(1);
        MachineId machineId2 = new MachineId(1);
        Assertions.assertEquals(machineId1.hashCode(), machineId2.hashCode());
    }
}
