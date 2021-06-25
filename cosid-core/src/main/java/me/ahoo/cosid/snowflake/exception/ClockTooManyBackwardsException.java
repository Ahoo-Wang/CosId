package me.ahoo.cosid.snowflake.exception;

/**
 * @author ahoo wang
 */
public class ClockTooManyBackwardsException extends ClockBackwardsException {

    public ClockTooManyBackwardsException(long lastStamp, long currentStamp) {
        super(lastStamp, currentStamp);
    }
}
