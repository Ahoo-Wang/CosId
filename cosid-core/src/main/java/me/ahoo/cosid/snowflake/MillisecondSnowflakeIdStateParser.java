package me.ahoo.cosid.snowflake;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

import static java.time.temporal.ChronoField.*;

/**
 * @author ahoo wang
 * Creation time :2020/9/23 11:22
 */
public class MillisecondSnowflakeIdStateParser extends SnowflakeIdStateParser {

    public static final DateTimeFormatter DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(YEAR, 4)
            .appendValue(MONTH_OF_YEAR, 2)
            .appendValue(DAY_OF_MONTH, 2)
            .appendValue(HOUR_OF_DAY, 2)
            .appendValue(MINUTE_OF_HOUR, 2)
            .appendValue(SECOND_OF_MINUTE, 2)
            .appendValue(MILLI_OF_SECOND, 3)
            .toFormatter();

    public MillisecondSnowflakeIdStateParser(long epoch, int timestampBit, int machineBit, int sequenceBit) {
        super(epoch, timestampBit, machineBit, sequenceBit);
    }

    @Override
    protected DateTimeFormatter getDateTimeFormatter() {
        return DATE_TIME_FORMATTER;
    }

    @Override
    protected LocalDateTime getTimestamp(long diffTime) {
        return Instant.ofEpochMilli(epoch + diffTime).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    @Override
    protected long getDiffTime(LocalDateTime timestamp) {
        return ZonedDateTime.of(timestamp, ZoneId.systemDefault()).toInstant().toEpochMilli() - epoch;
    }

    public static MillisecondSnowflakeIdStateParser of(SnowflakeId snowflakeId) {
        return new MillisecondSnowflakeIdStateParser(snowflakeId.getEpoch(), snowflakeId.getTimestampBit(), snowflakeId.getMachineBit(), snowflakeId.getSequenceBit());
    }
}
