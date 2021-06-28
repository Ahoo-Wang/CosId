package me.ahoo.cosid.snowflake.exception;

/**
 * @author ahoo wang
 */
public class ClockBackwardsException extends RuntimeException {
    private final long lastTimestamp;
    private final long currentTimestamp;

    public ClockBackwardsException(long lastTimestamp, long currentTimestamp) {
        super(String.format("Clock moved backwards.  Refusing to generate id. lastTimestamp:[%s] | currentTimestamp:[%s]", lastTimestamp, currentTimestamp));
        this.lastTimestamp = lastTimestamp;
        this.currentTimestamp = currentTimestamp;
    }

    public long getLastTimestamp() {
        return lastTimestamp;
    }

    public long getCurrentTimestamp() {
        return currentTimestamp;
    }
}
