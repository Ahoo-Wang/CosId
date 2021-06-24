package me.ahoo.cosid;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

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

    CompletableFuture<Integer> distributeAsync(String namespace, int machineBit, InstanceId instanceId);

    void revert(String namespace, InstanceId instanceId) throws MachineIdOverflowException;

    CompletableFuture<Void> revertAsync(String namespace, InstanceId instanceId);

    @Slf4j
    class ManualMachineIdDistributor implements MachineIdDistributor {
        private final int machineId;

        public ManualMachineIdDistributor(int machineId) {
            this.machineId = machineId;
        }


        @Override
        public int distribute(String namespace, int machineBit, InstanceId instanceId) {
            if (log.isInfoEnabled()) {
                log.info("distribute - machineId:[{}]", machineId);
            }
            return machineId;
        }

        @Override
        public CompletableFuture<Integer> distributeAsync(String namespace, int machineBit, InstanceId instanceId) {
            if (log.isInfoEnabled()) {
                log.info("distribute - machineId:[{}]", machineId);
            }
            return CompletableFuture.completedFuture(machineId);
        }

        @Override
        public void revert(String namespace, InstanceId instanceId) {

        }

        @Override
        public CompletableFuture<Void> revertAsync(String namespace, InstanceId instanceId) {
            return CompletableFuture.completedFuture(null);
        }
    }

}
