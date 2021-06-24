package me.ahoo.cosid.k8s;

import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.InstanceId;
import me.ahoo.cosid.MachineIdDistributor;

import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
@Slf4j
public class StatefulSetMachineIdDistributor implements MachineIdDistributor {
    public static final StatefulSetMachineIdDistributor INSTANCE = new StatefulSetMachineIdDistributor();
    public static final String HOSTNAME_KEY = "HOSTNAME";

    @Override
    public int distribute(String namespace, int machineBit, InstanceId instanceId) {
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
    public CompletableFuture<Integer> distributeAsync(String namespace, int machineBit, InstanceId instanceId) {
        return CompletableFuture.completedFuture(distribute(namespace, machineBit, instanceId));
    }

    @Override
    public void revert(String namespace, InstanceId instanceId) {

    }

    @Override
    public CompletableFuture<Void> revertAsync(String namespace, InstanceId instanceId) {
        return CompletableFuture.completedFuture(null);
    }

}
