package me.ahoo.cosid.snowflake.exception;

/**
 * @author ahoo wang
 */
public class ClockBackwardsException extends RuntimeException {
    private final long lastStamp;
    private final long currentStamp;

    public ClockBackwardsException(long lastStamp, long currentStamp) {
        super(String.format("Clock moved backwards.  Refusing to generate id. lastStamp:[%s] | currentStamp:[%s]", lastStamp, currentStamp));
        this.lastStamp = lastStamp;
        this.currentStamp = currentStamp;
    }

    public long getLastStamp() {
        return lastStamp;
    }

    public long getCurrentStamp() {
        return currentStamp;
    }
}
