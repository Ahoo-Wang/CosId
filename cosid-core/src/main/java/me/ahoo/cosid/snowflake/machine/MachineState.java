package me.ahoo.cosid.snowflake.machine;

/**
 * @author ahoo wang
 */
public class MachineState {
    public static final MachineState NOT_FOUND = of(-1, -1);
    private final int machineId;
    private final long lastTimeStamp;

    public MachineState(int machineId, long lastTimeStamp) {
        this.machineId = machineId;
        this.lastTimeStamp = lastTimeStamp;
    }

    public int getMachineId() {
        return machineId;
    }

    public long getLastTimeStamp() {
        return lastTimeStamp;
    }

    @Override
    public String toString() {
        return "MachineState{" +
                "machineId=" + machineId +
                ", lastTimeStamp=" + lastTimeStamp +
                '}';
    }

    public static MachineState of(int machineId, long lastStamp) {
        return new MachineState(machineId, lastStamp);
    }
}
