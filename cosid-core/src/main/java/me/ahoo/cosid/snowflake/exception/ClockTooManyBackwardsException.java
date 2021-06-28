package me.ahoo.cosid.snowflake.exception;

import me.ahoo.cosid.CosIdException;

/**
 * @author ahoo wang
 */
public class ClockTooManyBackwardsException extends CosIdException {

    private final long lastTimestamp;
    private final long currentTimestamp;
    private final long brokenThreshold;

    public ClockTooManyBackwardsException(long lastTimestamp, long currentTimestamp, long brokenThreshold) {
        super(String.format("Clock moved backwards too many.  brokenThreshold:[%s] | lastTimestamp:[%s] | currentTimestamp:[%s]", brokenThreshold, lastTimestamp, currentTimestamp));
        this.lastTimestamp = lastTimestamp;
        this.currentTimestamp = currentTimestamp;
        this.brokenThreshold = brokenThreshold;
    }

    public long getLastTimestamp() {
        return lastTimestamp;
    }

    public long getCurrentTimestamp() {
        return currentTimestamp;
    }

    public long getBrokenThreshold() {
        return brokenThreshold;
    }
}
