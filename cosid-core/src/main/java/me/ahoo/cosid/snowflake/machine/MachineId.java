package me.ahoo.cosid.snowflake.machine;

import com.google.common.base.Objects;

/**
 * 逻辑概念的机器号，并不一定跟物理机/虚拟机一一对应，运行进程的唯一性编号(不同业务领域/服务使用 namespace 隔离)。
 * @see InstanceId
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
