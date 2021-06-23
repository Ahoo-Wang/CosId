package me.ahoo.cosid;

/**
 * @author ahoo wang
 */
public interface MachineIdDistributor {
    int distribute();

    class DefaultMachineIdDistributor implements MachineIdDistributor {
        private final int machineId;

        public DefaultMachineIdDistributor(int machineId) {
            this.machineId = machineId;
        }

        @Override
        public int distribute() {
            return machineId;
        }
    }
}
