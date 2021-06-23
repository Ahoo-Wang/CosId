package me.ahoo.cosid.snowflake;

import me.ahoo.cosid.CosId;

import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 * Creation time 2020/9/22 20:14
 **/
public class SecondSnowflakeId extends SnowflakeId {

    public final static int DEFAULT_TIMESTAMP_BIT = 31;
    public final static int DEFAULT_MACHINE_BIT = 10;
    public final static int DEFAULT_SEQUENCE_BIT = 22;

    public SecondSnowflakeId(int machineId) {
        this(CosId.COSID_EPOCH_SECOND, DEFAULT_TIMESTAMP_BIT, DEFAULT_MACHINE_BIT, DEFAULT_SEQUENCE_BIT, machineId);
    }

    public SecondSnowflakeId(long epoch, int timestampBit, int machineBit, int sequenceBit, int machineId) {
        super(epoch, timestampBit, machineBit, sequenceBit, machineId);
    }

    @Override
    protected long getCurrentTime() {
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    }
}
