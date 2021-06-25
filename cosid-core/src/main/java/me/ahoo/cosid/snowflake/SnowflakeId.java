package me.ahoo.cosid.snowflake;

import lombok.var;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.snowflake.exception.ClockBackwardsException;
import me.ahoo.cosid.snowflake.exception.TimestampOverflowException;

/**
 * @author ahoo wang
 * Creation time: 2019/11/26 16:14
 */
public abstract class SnowflakeId implements IdGenerator {

    public final static int TOTAL_BIT = 63;

    protected final long epoch;
    protected final int timestampBit;
    protected final int machineBit;
    protected final int sequenceBit;

    protected final long maxTimestamp;
    protected final long maxSequence;
    protected final long maxMachine;

    protected final long machineLeft;
    protected final long timestampLeft;

    protected final int machineId;
    protected long sequence = 0L;
    protected long lastStamp = -1L;

    public SnowflakeId(long epoch, int timestampBit, int machineBit, int sequenceBit, int machineId) {
        if ((timestampBit + machineBit + sequenceBit) > TOTAL_BIT) {
            throw new IllegalArgumentException("total bit can't be greater than TOTAL_BIT[63] .");
        }
        this.epoch = epoch;
        this.timestampBit = timestampBit;
        this.machineBit = machineBit;
        this.sequenceBit = sequenceBit;
        this.maxTimestamp = -1L ^ (-1L << timestampBit);
        this.maxSequence = -1L ^ (-1L << sequenceBit);
        this.maxMachine = -1L ^ (-1L << machineBit);
        this.machineLeft = sequenceBit;
        this.timestampLeft = this.machineLeft + machineBit;
        if (machineId > this.maxMachine || machineId < 0) {
            throw new IllegalArgumentException("machineId can't be greater than maxMachine or less than 0 .");
        }
        this.machineId = machineId;
    }

    protected long nextTime() {
        long time = getCurrentTime();
        while (time <= lastStamp) {
            time = getCurrentTime();
        }
        return time;
    }

    /**
     * 获取当前时间戳
     *
     * @return 当前时间戳
     */
    protected abstract long getCurrentTime();

    @Override
    public synchronized long generate() {
        long currentStamp = getCurrentTime();
        if (currentStamp < lastStamp) {
            throw new ClockBackwardsException(lastStamp, currentStamp);
        }

        if (currentStamp == lastStamp) {
            sequence = (sequence + 1) & maxSequence;
            if (sequence == 0L) {
                currentStamp = nextTime();
            }
        } else {
            sequence = 0L;
        }

        lastStamp = currentStamp;
        var diffStamp = (currentStamp - epoch);
        if (diffStamp > maxTimestamp) {
            throw new TimestampOverflowException(epoch, diffStamp, maxTimestamp);
        }
        return diffStamp << timestampLeft
                | machineId << machineLeft
                | sequence;
    }

    public long getEpoch() {
        return epoch;
    }

    public int getTimestampBit() {
        return timestampBit;
    }

    public int getMachineBit() {
        return machineBit;
    }

    public int getSequenceBit() {
        return sequenceBit;
    }

    public boolean isSafeJavascript() {
        return (timestampBit + machineBit + sequenceBit) <= SafeJavaScriptSnowflakeId.JAVA_SCRIPT_MAX_SAFE_NUMBER_BIT;
    }

    public long getMaxTimestamp() {
        return maxTimestamp;
    }

    public long getMaxMachine() {
        return maxMachine;
    }

    public long getMaxSequence() {
        return maxSequence;
    }

    public long getLastStamp() {
        return lastStamp;
    }
}
