package me.ahoo.cosid.snowflake;

import me.ahoo.cosid.CosId;

/**
 * Safe JavaScript Number Id
 * Number.MAX_SAFE_INTEGER = 9007199254740991
 * Math.log2(Number.MAX_SAFE_INTEGER) = 53
 *
 * @author ahoo wang
 * Creation time 2020/9/22 21:25
 **/
public class SafeJavaScriptSnowflakeId {

    public static final int JAVA_SCRIPT_MAX_SAFE_NUMBER_BIT = 53;
    public static final long JAVA_SCRIPT_MAX_SAFE_NUMBER = 9007199254740991L;

    public static MillisecondSnowflakeId ofMillisecond(long epoch, int timestampBit, int machineBit, int sequenceBit, int machineId) {
        checkTotalBit(timestampBit, machineBit, sequenceBit);
        return new MillisecondSnowflakeId(epoch, timestampBit, machineBit, sequenceBit, machineId);
    }

    public static SecondSnowflakeId ofSecond(long epoch, int timestampBit, int machineBit, int sequenceBit, int machineId) {
        checkTotalBit(timestampBit, machineBit, sequenceBit);
        return new SecondSnowflakeId(epoch, timestampBit, machineBit, sequenceBit, machineId);
    }

    /**
     * Max Sequence (TPS) = 63000
     * Max Machine = 63
     * Max Timestamp = 2199023255551 ms ~~ 69.7 years
     * @param machineId 服务实例编号
     * @return MillisecondSnowflakeId
     */
    public static MillisecondSnowflakeId ofMillisecond(int machineId) {
        int timestampBit = MillisecondSnowflakeId.DEFAULT_TIMESTAMP_BIT;
        int machineBit = MillisecondSnowflakeId.DEFAULT_MACHINE_BIT - 4;
        int sequenceBit = MillisecondSnowflakeId.DEFAULT_SEQUENCE_BIT - 6;
        checkTotalBit(timestampBit, machineBit, sequenceBit);
        return ofMillisecond(CosId.COSID_EPOCH_SECOND, timestampBit, machineBit, sequenceBit, machineId);
    }

    /**
     * Max Sequence (TPS) = 65535
     * Max Machine = 63
     * Max Timestamp = 2147483647 s ~~ 68 years
     *
     * @param machineId 服务实例编号
     * @return SecondSnowflakeId
     */
    public static SecondSnowflakeId ofSecond(int machineId) {
        int timestampBit = SecondSnowflakeId.DEFAULT_TIMESTAMP_BIT;
        int machineBit = SecondSnowflakeId.DEFAULT_MACHINE_BIT - 4;
        int sequenceBit = SecondSnowflakeId.DEFAULT_SEQUENCE_BIT - 6;
        checkTotalBit(timestampBit, machineBit, sequenceBit);
        return ofSecond(CosId.COSID_EPOCH_SECOND, timestampBit, machineBit, sequenceBit, machineId);
    }

    private static void checkTotalBit(int timestampBit, int machineBit, int sequenceBit) {
        if (timestampBit + machineBit + sequenceBit > JAVA_SCRIPT_MAX_SAFE_NUMBER_BIT) {
            throw new IllegalArgumentException(String.format("total bit can't be greater than JAVA_SCRIPT_MAX_SAFE_NUMBER_BIT:[%s].", JAVA_SCRIPT_MAX_SAFE_NUMBER_BIT));
        }
    }
}
