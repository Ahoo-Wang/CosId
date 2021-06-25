package me.ahoo.cosid.snowflake.exception;

import com.google.common.base.Strings;

/**
 * @author ahoo wang
 */
public class TimestampOverflowException extends RuntimeException {
    private final long epoch;
    private final long diffStamp;
    private final long maxTimestamp;

    public TimestampOverflowException(long epoch, long diffStamp, long maxTimestamp) {
        super(Strings.lenientFormat("epoch:[%s] - diffStamp:[%s] can't be greater than maxTimestamp:[%s]", epoch, diffStamp, maxTimestamp));
        this.epoch = epoch;
        this.diffStamp = diffStamp;
        this.maxTimestamp = maxTimestamp;
    }

    public long getEpoch() {
        return epoch;
    }

    public long getDiffStamp() {
        return diffStamp;
    }

    public long getMaxTimestamp() {
        return maxTimestamp;
    }
}
