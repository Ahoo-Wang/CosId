package me.ahoo.cosid.snowflake;

import me.ahoo.cosid.IdGenerator;

/**
 * @author ahoo wang
 */
public interface SnowflakeId extends IdGenerator {
    int TOTAL_BIT = 63;

    long getEpoch();

    int getTimestampBit();

    int getMachineBit();

    int getSequenceBit();

    /**
     * 是否是 Javascript  安全的 SnowflakeId
     * {@link SafeJavaScriptSnowflakeId#JAVA_SCRIPT_MAX_SAFE_NUMBER_BIT}
     *
     * @return
     */
    default boolean isSafeJavascript() {
        return (getTimestampBit() + getMachineBit() + getSequenceBit()) <= SafeJavaScriptSnowflakeId.JAVA_SCRIPT_MAX_SAFE_NUMBER_BIT;
    }

    long getMaxTimestamp();

    long getMaxMachine();

    long getMaxSequence();

    long getLastTimestamp();

    int getMachineId();
}
