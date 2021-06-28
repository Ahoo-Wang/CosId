package me.ahoo.cosid.snowflake;

import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.snowflake.exception.ClockBackwardsException;

/**
 * @author ahoo wang
 */
@Slf4j
public class ClockSyncSnowflakeId implements IdGenerator {

    private final SnowflakeId snowflakeId;
    private final ClockBackwardsSynchronizer clockBackwardsSynchronizer;

    public ClockSyncSnowflakeId(SnowflakeId snowflakeId, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        this.snowflakeId = snowflakeId;
        this.clockBackwardsSynchronizer = clockBackwardsSynchronizer;
    }

    @Override
    public long generate() {
        try {
            return snowflakeId.generate();
        } catch (ClockBackwardsException exception) {
            if (log.isWarnEnabled()) {
                log.warn(exception.getMessage(), exception);
            }
            clockBackwardsSynchronizer.syncUninterruptibly(snowflakeId.getLastStamp());
            return snowflakeId.generate();
        }
    }
}
