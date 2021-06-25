package me.ahoo.cosid.snowflake.machine;


/**
 * @author ahoo wang
 */
public interface MachineIdDistributor {

    default int maxMachineId(int machineBit) {
        return -1 ^ (-1 << machineBit);
    }

    default int totalMachineIds(int machineBit) {
        return maxMachineId(machineBit) + 1;
    }

    /**
     * @param namespace
     * @param machineBit
     * @param instanceId
     * @return
     * @throws MachineIdOverflowException
     */
    int distribute(String namespace, int machineBit, InstanceId instanceId) throws MachineIdOverflowException;

    void revert(String namespace, InstanceId instanceId) throws MachineIdOverflowException;


}
