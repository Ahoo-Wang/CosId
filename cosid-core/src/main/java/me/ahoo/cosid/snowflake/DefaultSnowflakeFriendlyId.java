package me.ahoo.cosid.snowflake;

/**
 * @author ahoo wang
 */
public class DefaultSnowflakeFriendlyId implements SnowflakeFriendlyId {
    private final SnowflakeId delegate;
    private final SnowflakeIdStateParser snowflakeIdStateParser;

    public DefaultSnowflakeFriendlyId(SnowflakeId delegate) {
        this.delegate = delegate;
        this.snowflakeIdStateParser = SnowflakeIdStateParser.of(delegate);
    }

    public SnowflakeId getDelegate() {
        return delegate;
    }

    public SnowflakeIdStateParser getSnowflakeIdStateParser() {
        return snowflakeIdStateParser;
    }

    @Override
    public long generate() {
        return delegate.generate();
    }

    @Override
    public SnowflakeIdState friendlyId(long id) {
        return snowflakeIdStateParser.parse(id);
    }

    @Override
    public SnowflakeIdState ofFriendlyId(String friendlyId) {
        return snowflakeIdStateParser.parse(friendlyId);
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
