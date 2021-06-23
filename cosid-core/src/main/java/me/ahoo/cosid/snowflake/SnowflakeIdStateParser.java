package me.ahoo.cosid.snowflake;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import lombok.var;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author ahoo wang
 * Creation time: 2019/12/4 21:20
 */
public abstract class SnowflakeIdStateParser {

    public static final String DELIMITER = "-";

    protected final long epoch;

    protected final int sequenceBit;
    protected final long sequenceMask;

    protected final int machineBit;
    protected final long machineMask;
    protected final int machineLeft;

    protected final int timestampBit;
    protected final long timestampMask;
    protected final int timestampLeft;

    public SnowflakeIdStateParser(long epoch
            , int timestampBit, int machineBit, int sequenceBit) {
        this.epoch = epoch;
        this.sequenceMask = getMask(sequenceBit);
        this.sequenceBit = sequenceBit;
        this.machineMask = getMask(machineBit);
        this.machineBit = machineBit;
        this.timestampMask = getMask(timestampBit);
        this.timestampBit = timestampBit;

        this.machineLeft = sequenceBit;
        this.timestampLeft = machineLeft + machineBit;
    }

    protected abstract DateTimeFormatter getDateTimeFormatter();

    protected abstract LocalDateTime getTimestamp(long diffTime);

    protected abstract long getDiffTime(LocalDateTime timestamp);

    public SnowflakeIdState parse(String friendlyId) {
        Preconditions.checkNotNull(friendlyId, "friendlyId can not be null!");
        var segments = Splitter.on(DELIMITER).trimResults().omitEmptyStrings().splitToList(friendlyId);
        if (segments.size() != 3) {
            throw new IllegalArgumentException(Strings.lenientFormat("friendlyId :[%s] Illegal.", friendlyId));
        }
        var timestampStr = segments.get(0);
        var timestamp = LocalDateTime.parse(timestampStr, getDateTimeFormatter());
        var machineId = Integer.parseInt(segments.get(1));
        var sequence = Long.parseLong(segments.get(2));
        var diffTime = getDiffTime(timestamp);
        var id = (diffTime) << timestampLeft
                | machineId << machineLeft
                | sequence;
        return SnowflakeIdState.builder()
                .id(id)
                .machineId(machineId)
                .sequence(sequence)
                .timestamp(timestamp)
                .friendlyId(friendlyId)
                .build();
    }

    public SnowflakeIdState parse(long id) {

        var machineId = (id >> machineLeft) & machineMask;
        var sequence = id & sequenceMask;
        var diffTime = (id >> timestampLeft) & timestampMask;

        var timestamp = getTimestamp(diffTime);

        var friendlyId = new StringBuilder(timestamp.format(getDateTimeFormatter()))
                .append(DELIMITER)
                .append(machineId)
                .append(DELIMITER)
                .append(sequence)
                .toString();

        return SnowflakeIdState.builder()
                .id(id)
                .machineId((int) machineId)
                .sequence(sequence)
                .timestamp(timestamp)
                .friendlyId(friendlyId)
                .build();
    }

    private long getMask(long bits) {
        return -1L ^ (-1L << bits);
    }


    public static SnowflakeIdStateParser of(SnowflakeId snowflakeId) {
        if (SecondSnowflakeId.class.isInstance(snowflakeId)) {
            return SecondSnowflakeIdStateParser.of(snowflakeId);
        }
        return MillisecondSnowflakeIdStateParser.of(snowflakeId);
    }
}
