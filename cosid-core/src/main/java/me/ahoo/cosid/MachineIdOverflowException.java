package me.ahoo.cosid;

import com.google.common.base.Strings;

/**
 * @author ahoo wang
 */
public class MachineIdOverflowException extends RuntimeException {
    private final int totalMachineIds;
    private final InstanceId instanceId;

    public MachineIdOverflowException(int totalMachineIds, InstanceId instanceId) {
        super(Strings.lenientFormat("InstanceId:[%s] - distribution failed - totalMachineIds:[%s]", instanceId, totalMachineIds));
        this.totalMachineIds = totalMachineIds;
        this.instanceId = instanceId;
    }

    public int getTotalMachineIds() {
        return totalMachineIds;
    }

    public InstanceId getInstanceId() {
        return instanceId;
    }
}
