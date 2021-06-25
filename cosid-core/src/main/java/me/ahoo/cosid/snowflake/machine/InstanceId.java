package me.ahoo.cosid.snowflake.machine;

/**
 * @author ahoo wang
 */
public interface InstanceId {
    InstanceId NONE = new DefaultInstanceId("none", false);

    /**
     * 稳定的的实例拥有稳定的机器号
     *
     * @return
     */
    default boolean isStable() {
        return false;
    }

    String getInstanceId();


}
