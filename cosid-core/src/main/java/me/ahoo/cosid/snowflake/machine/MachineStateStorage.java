package me.ahoo.cosid.snowflake.machine;

/**
 * 机器状态的本地缓存
 *
 * @author ahoo wang
 */
public interface MachineStateStorage {
    MachineStateStorage LOCAL = new LocalMachineStateStorage();
    MachineStateStorage NONE = new None();

    MachineState get(String namespace, InstanceId instanceId);

    void set(String namespace, int machineId, InstanceId instanceId);

    void remove(String namespace, InstanceId instanceId);

    void clear(String namespace);

    int size(String namespace);

    boolean exists(String namespace, InstanceId instanceId);

    class None implements MachineStateStorage {
        @Override
        public MachineState get(String namespace, InstanceId instanceId) {
            return MachineState.NOT_FOUND;
        }

        @Override
        public void set(String namespace, int machineId, InstanceId instanceId) {

        }

        @Override
        public void remove(String namespace, InstanceId instanceId) {

        }

        @Override
        public void clear(String namespace) {

        }

        @Override
        public int size(String namespace) {
            return 0;
        }


        @Override
        public boolean exists(String namespace, InstanceId instanceId) {
            return false;
        }
    }
}
