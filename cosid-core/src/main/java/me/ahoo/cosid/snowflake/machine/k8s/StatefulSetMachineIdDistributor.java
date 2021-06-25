package me.ahoo.cosid.snowflake.machine.k8s;

import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.snowflake.machine.*;

import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
@Slf4j
public class StatefulSetMachineIdDistributor extends AbstractMachineIdDistributor {
    public static final StatefulSetMachineIdDistributor INSTANCE = new StatefulSetMachineIdDistributor();
    public static final String HOSTNAME_KEY = "HOSTNAME";

    public StatefulSetMachineIdDistributor() {
        super(LocalMachineState.FILE);
    }

    public StatefulSetMachineIdDistributor(LocalMachineState localMachineState) {
        super(localMachineState);
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
