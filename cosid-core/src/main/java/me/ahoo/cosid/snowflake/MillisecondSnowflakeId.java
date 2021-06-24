package me.ahoo.cosid.snowflake;

import me.ahoo.cosid.CosId;

/**
 * @author ahoo wang
 * Creation time 2020/9/22 20:13
 **/
public class MillisecondSnowflakeId extends SnowflakeId {

    public final static int DEFAULT_TIMESTAMP_BIT = 41;
    public final static int DEFAULT_MACHINE_BIT = 10;
    public final static int DEFAULT_SEQUENCE_BIT = 12;

    public MillisecondSnowflakeId(int machineId) {
        this(CosId.COSID_EPOCH, DEFAULT_TIMESTAMP_BIT, DEFAULT_MACHINE_BIT, DEFAULT_SEQUENCE_BIT, machineId);
    }

    public MillisecondSnowflakeId(int machineBit, int machineId) {
        super(CosId.COSID_EPOCH, DEFAULT_TIMESTAMP_BIT, machineBit, DEFAULT_SEQUENCE_BIT, machineId);
    }

    public MillisecondSnowflakeId(long epoch, int timestampBit, int machineBit, int sequenceBit, int machineId) {
        super(epoch, timestampBit, machineBit, sequenceBit, machineId);
    }

    @Override
    protected long getCurrentTime() {
        return System.currentTimeMillis();
    }
}
