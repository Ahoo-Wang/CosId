package me.ahoo.cosid.snowflake.exception;

import com.google.common.base.Strings;

/**
 * @author ahoo wang
 */
public class TimestampOverflowException extends RuntimeException {
    private final long epoch;
    private final long diffTimestamp;
    private final long maxTimestamp;

    public TimestampOverflowException(long epoch, long diffTimestamp, long maxTimestamp) {
        super(Strings.lenientFormat("epoch:[%s] - diffTimestamp:[%s] can't be greater than maxTimestamp:[%s]", epoch, diffTimestamp, maxTimestamp));
        this.epoch = epoch;
        this.diffTimestamp = diffTimestamp;
        this.maxTimestamp = maxTimestamp;
    }

    public long getEpoch() {
        return epoch;
    }

    public long getDiffTimestamp() {
        return diffTimestamp;
    }

    public long getMaxTimestamp() {
        return maxTimestamp;
    }
}
