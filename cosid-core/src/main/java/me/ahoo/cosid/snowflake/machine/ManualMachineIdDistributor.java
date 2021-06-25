package me.ahoo.cosid.snowflake.machine;

import lombok.extern.slf4j.Slf4j;

/**
 * @author ahoo wang
 */
@Slf4j
public class ManualMachineIdDistributor extends AbstractMachineIdDistributor {

    private final int machineId;
    private final MachineState machineState;

    public ManualMachineIdDistributor(int machineId) {
        super(LocalMachineState.FILE);
        this.machineId = machineId;
        this.machineState = MachineState.of(machineId, NOT_FOUND_LAST_STAMP);
    }

    public int getMachineId() {
        return machineId;
    }

    @Override
    protected MachineState distribute0(String namespace, int machineBit, InstanceId instanceId) {
        return machineState;
    }

    @Override
    protected void revert0(String namespace, InstanceId instanceId, MachineState machineState) {

    }


}
