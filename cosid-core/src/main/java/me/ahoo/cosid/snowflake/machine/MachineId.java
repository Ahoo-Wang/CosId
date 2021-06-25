package me.ahoo.cosid.snowflake.machine;

import com.google.common.base.Objects;

/**
 * @author ahoo wang
 */
public class MachineId {
    private final int machineId;

    public MachineId(int machineId) {
        this.machineId = machineId;
    }

    public int getMachineId() {
        return machineId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MachineId)) return false;
        MachineId machineId1 = (MachineId) o;
        return getMachineId() == machineId1.getMachineId();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getMachineId());
    }
}
