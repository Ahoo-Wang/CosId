package me.ahoo.cosid.snowflake.machine;

import com.google.common.base.Objects;

/**
 * @author ahoo wang
 */
public class DefaultInstanceId implements InstanceId {

    private final String instanceId;
    private final boolean stable;

    public DefaultInstanceId(String instanceId, boolean stable) {
        this.instanceId = instanceId;
        this.stable = stable;
    }

    @Override
    public boolean isStable() {
        return stable;
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public String toString() {
        return instanceId;
    }

    public static InstanceId of(String host, int port, boolean stable) {
        String instanceIdStr = String.format("%s:%s", host, port);
        return of(instanceIdStr, stable);
    }

    public static InstanceId of(String instanceId, boolean stable) {
        return new DefaultInstanceId(instanceId, stable);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DefaultInstanceId)) return false;
        DefaultInstanceId that = (DefaultInstanceId) o;
        return isStable() == that.isStable() && Objects.equal(getInstanceId(), that.getInstanceId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getInstanceId(), isStable());
    }
}
