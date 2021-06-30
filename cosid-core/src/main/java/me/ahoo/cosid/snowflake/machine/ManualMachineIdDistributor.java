package me.ahoo.cosid.snowflake.machine;

import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;

/**
 * @author ahoo wang
 */
@Slf4j
public class ManualMachineIdDistributor extends AbstractMachineIdDistributor {

    private final int machineId;
    private final MachineState machineState;

    public ManualMachineIdDistributor(int machineId, MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        super(machineStateStorage, clockBackwardsSynchronizer);
        this.machineId = machineId;
        this.machineState = MachineState.of(machineId, NOT_FOUND_LAST_STAMP);
    }

    public int getMachineId() {
        return machineId;
    }

    @Override
    protected MachineState distribute0(String namespace, int machineBit, InstanceId instanceId) {
        if (log.isInfoEnabled()) {
            log.info("distribute0 - machineState:[{}] - instanceId:[{}] - machineBit:[{}] @ namespace:[{}].", machineState, instanceId, machineBit, namespace);
        }
        return machineState;
    }

    @Override
    protected void revert0(String namespace, InstanceId instanceId, MachineState machineState) {

    }


}
