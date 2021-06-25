package me.ahoo.cosid.snowflake.machine;

/**
 * @author ahoo wang
 */
public class MachineState {
    public static final MachineState NOT_FOUND = of(-1, -1);
    private final int machineId;
    private final long lastStamp;

    public MachineState(int machineId, long lastStamp) {
        this.machineId = machineId;
        this.lastStamp = lastStamp;
    }

    public int getMachineId() {
        return machineId;
    }

    public long getLastStamp() {
        return lastStamp;
    }

    public static MachineState of(int machineId, long lastStamp) {
        return new MachineState(machineId, lastStamp);
    }
}
