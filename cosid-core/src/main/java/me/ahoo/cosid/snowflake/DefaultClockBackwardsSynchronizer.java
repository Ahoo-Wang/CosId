package me.ahoo.cosid.snowflake;

import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.snowflake.exception.ClockTooManyBackwardsException;

import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 */
@Slf4j
public class DefaultClockBackwardsSynchronizer implements ClockBackwardsSynchronizer {
    private final int spinThreshold;
    private final int brokenThreshold;

    public DefaultClockBackwardsSynchronizer() {
        this(20, 2000);
    }

    public DefaultClockBackwardsSynchronizer(int spinThreshold, int brokenThreshold) {
        this.spinThreshold = spinThreshold;
        this.brokenThreshold = brokenThreshold;
    }

    @Override
    public void sync(long lastStamp) throws InterruptedException, ClockTooManyBackwardsException {
        long backwardsStamp = ClockBackwardsSynchronizer.getBackwardsStamp(lastStamp);
        if (backwardsStamp <= 0) {
            return;
        }
        if (log.isWarnEnabled()) {
            log.warn("sync - backwardsStamp:[{}] - lastStamp:[{}].", backwardsStamp, lastStamp);
        }

        if (backwardsStamp <= spinThreshold) {
            while ((ClockBackwardsSynchronizer.getBackwardsStamp(lastStamp)) <= 0) {
                /**
                 * Spin until it catches the clock back
                 */
            }
        }

        if (backwardsStamp > brokenThreshold) {
            throw new ClockTooManyBackwardsException(lastStamp, System.currentTimeMillis());
        }

        TimeUnit.MILLISECONDS.sleep(backwardsStamp);
    }

    @Override
    public void syncUninterruptibly(long lastStamp) throws ClockTooManyBackwardsException {
        try {
            sync(lastStamp);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CosIdException(e);
        }
    }
}
