package me.ahoo.cosid.snowflake;

import me.ahoo.cosid.snowflake.exception.ClockTooManyBackwardsException;

/**
 * 时钟回拨同步器
 *
 * @author ahoo wang
 */
public interface ClockBackwardsSynchronizer {
    ClockBackwardsSynchronizer DEFAULT = new DefaultClockBackwardsSynchronizer();

    void sync(long lastTimestamp) throws InterruptedException, ClockTooManyBackwardsException;

    void syncUninterruptibly(long lastTimestamp) throws ClockTooManyBackwardsException;

    static long getBackwardsTimeStamp(long lastTimestamp) {
        return lastTimestamp - System.currentTimeMillis();
    }
}

