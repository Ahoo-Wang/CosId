package me.ahoo.cosid.snowflake.machine;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author ahoo wang
 */
class LocalMachineStateStorageTest {
    private static final String namespace = "test";
    private final LocalMachineStateStorage fileLocalMachineState = new LocalMachineStateStorage();

    @Test
    void get() {
        fileLocalMachineState.remove(namespace, InstanceId.NONE);
        MachineState machineState = fileLocalMachineState.get(namespace, InstanceId.NONE);
        Assertions.assertEquals(-1, machineState.getMachineId());

        fileLocalMachineState.set(namespace, 1, InstanceId.NONE);
        machineState = fileLocalMachineState.get(namespace, InstanceId.NONE);
        Assertions.assertEquals(1, machineState.getMachineId());
    }

    @Test
    void set() {
        fileLocalMachineState.set(namespace, 1, InstanceId.NONE);
        MachineState machineState = fileLocalMachineState.get(namespace, InstanceId.NONE);
        Assertions.assertEquals(1, machineState.getMachineId());
        fileLocalMachineState.set(namespace, 2, InstanceId.NONE);
        machineState = fileLocalMachineState.get(namespace, InstanceId.NONE);
        Assertions.assertEquals(2, machineState.getMachineId());
    }

    @Test
    void remove() {
        fileLocalMachineState.remove(namespace, InstanceId.NONE);
        Assertions.assertTrue(!fileLocalMachineState.exists(namespace, InstanceId.NONE));
    }

    @Test
    void clear() {
        fileLocalMachineState.clear(namespace);
        fileLocalMachineState.set(namespace, 1, InstanceId.of("test", false));
        fileLocalMachineState.set(namespace, 2, InstanceId.of("test1", false));
        fileLocalMachineState.clear(namespace);
        Assertions.assertEquals(0, fileLocalMachineState.size(namespace));
    }
}
