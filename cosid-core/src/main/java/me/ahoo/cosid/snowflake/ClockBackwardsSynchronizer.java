package me.ahoo.cosid.snowflake;

import me.ahoo.cosid.snowflake.exception.ClockTooManyBackwardsException;

/**
 * 时钟回拨同步器
 *
 * @author ahoo wang
 */
public interface ClockBackwardsSynchronizer {
    ClockBackwardsSynchronizer DEFAULT = new DefaultClockBackwardsSynchronizer();

    void sync(long lastStamp) throws InterruptedException, ClockTooManyBackwardsException;

    void syncUninterruptibly(long lastStamp) throws ClockTooManyBackwardsException;

    static long getBackwardsStamp(long lastStamp) {
        return lastStamp - System.currentTimeMillis();
    }
}

