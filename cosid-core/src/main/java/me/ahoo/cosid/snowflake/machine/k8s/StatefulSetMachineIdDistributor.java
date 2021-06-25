package me.ahoo.cosid.snowflake.machine.k8s;

import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.machine.*;

/**
 * @author ahoo wang
 */
@Slf4j
public class StatefulSetMachineIdDistributor extends AbstractMachineIdDistributor {
    public static final StatefulSetMachineIdDistributor INSTANCE = new StatefulSetMachineIdDistributor(LocalMachineState.FILE, ClockBackwardsSynchronizer.DEFAULT);
    public static final String HOSTNAME_KEY = "HOSTNAME";

    public StatefulSetMachineIdDistributor(LocalMachineState localMachineState, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        super(localMachineState, clockBackwardsSynchronizer);
    }


    public static int resolveMachineId() {
        String hostName = System.getenv(HOSTNAME_KEY);
        Preconditions.checkNotNull(hostName, "HOSTNAME can not be null.");
        int lastSplitIdx = hostName.lastIndexOf("-");

        String idStr = hostName.substring(lastSplitIdx + 1);
        if (log.isInfoEnabled()) {
            log.info("distribute - machineId:[{}] from Env HOSTNAME:[{}]", idStr, hostName);
        }
        return Integer.parseInt(idStr);
    }

    @Override
    protected MachineState distribute0(String namespace, int machineBit, InstanceId instanceId) {
        int machineId = resolveMachineId();
        return MachineState.of(machineId, NOT_FOUND_LAST_STAMP);
    }


    @Override
    protected void revert0(String namespace, InstanceId instanceId, MachineState machineState) {

    }

}
