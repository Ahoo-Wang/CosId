package me.ahoo.cosid.snowflake;

import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.snowflake.exception.ClockBackwardsException;

/**
 * @author ahoo wang
 */
@Slf4j
public class ClockSyncSnowflakeId implements SnowflakeId {

    private final SnowflakeId delegate;
    private final ClockBackwardsSynchronizer clockBackwardsSynchronizer;

    public ClockSyncSnowflakeId(SnowflakeId delegate) {
        this(delegate, ClockBackwardsSynchronizer.DEFAULT);
    }

    public ClockSyncSnowflakeId(SnowflakeId delegate, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        this.delegate = delegate;
        this.clockBackwardsSynchronizer = clockBackwardsSynchronizer;
    }

    @Override
    public long generate() {
        try {
            return delegate.generate();
        } catch (ClockBackwardsException exception) {
            if (log.isWarnEnabled()) {
                log.warn(exception.getMessage(), exception);
            }
            clockBackwardsSynchronizer.syncUninterruptibly(delegate.getLastTimestamp());
            return delegate.generate();
        }
    }

    public SnowflakeId getDelegate() {
        return delegate;
    }

    @Override
    public long getEpoch() {
        return delegate.getEpoch();
    }

    @Override
    public int getTimestampBit() {
        return delegate.getTimestampBit();
    }

    @Override
    public int getMachineBit() {
        return delegate.getMachineBit();
    }

    @Override
    public int getSequenceBit() {
        return delegate.getSequenceBit();
    }

    @Override
    public boolean isSafeJavascript() {
        return delegate.isSafeJavascript();
    }

    @Override
    public long getMaxTimestamp() {
        return delegate.getMaxTimestamp();
    }

    @Override
    public long getMaxMachine() {
        return delegate.getMaxMachine();
    }

    @Override
    public long getMaxSequence() {
        return delegate.getMaxSequence();
    }

    @Override
    public long getLastTimestamp() {
        return delegate.getLastTimestamp();
    }

    @Override
    public int getMachineId() {
        return delegate.getMachineId();
    }


}
