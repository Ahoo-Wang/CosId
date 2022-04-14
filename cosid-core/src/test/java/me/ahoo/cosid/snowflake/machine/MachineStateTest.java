package me.ahoo.cosid.snowflake.machine;

import com.google.common.base.Objects;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * MachineStateTest .
 *
 * @author ahoo wang
 */
class MachineStateTest {
    
    @Test
    void getMachineId() {
        Assertions.assertEquals(Objects.hashCode(MachineState.NOT_FOUND.getMachineId()), MachineState.NOT_FOUND.hashCode());
    }
    
    @Test
    void testEquals() {
        MachineState machineState1 = new MachineState(1, 0);
        MachineState machineState2 = new MachineState(1, 0);
        Assertions.assertEquals(machineState1, machineState2);
    }
    
    @Test
    void testHashCode() {
        Assertions.assertEquals(Objects.hashCode(MachineState.NOT_FOUND.getMachineId()), MachineState.NOT_FOUND.hashCode());
    }
    
    @Test
    void of() {
        Assertions.assertEquals(MachineState.NOT_FOUND,MachineState.of("-1|-1"));
    }

}
