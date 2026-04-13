/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.ahoo.cosid.snowflake;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

/**
 * Parser for second-based SnowflakeId state.
 *
 * <p>Handles conversion between second SnowflakeIds and their string
 * representations using format: {@code yyyyMMddHHmmss-machineId-sequence}.
 *
 * @author ahoo wang
 */
public class SecondSnowflakeIdStateParser extends SnowflakeIdStateParser {

    /**
     * DateTimeFormatter for timestamps: {@code yyyyMMddHHmmss}.
     */
    public static final DateTimeFormatter DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(YEAR, 4)
            .appendValue(MONTH_OF_YEAR, 2)
            .appendValue(DAY_OF_MONTH, 2)
            .appendValue(HOUR_OF_DAY, 2)
            .appendValue(MINUTE_OF_HOUR, 2)
            .appendValue(SECOND_OF_MINUTE, 2)
            .toFormatter();

    /**
     * Creates a parser with default zone and no padding.
     *
     * @param epoch        epoch timestamp
     * @param timestampBit bits for timestamp
     * @param machineBit  bits for machine ID
     * @param sequenceBit bits for sequence
     */
    public SecondSnowflakeIdStateParser(long epoch, int timestampBit, int machineBit, int sequenceBit) {
        this(epoch, timestampBit, machineBit, sequenceBit, ZoneId.systemDefault(), false);
    }

    /**
     * Creates a parser with custom zone and padding.
     *
     * @param epoch        epoch timestamp
     * @param timestampBit bits for timestamp
     * @param machineBit  bits for machine ID
     * @param sequenceBit bits for sequence
     * @param zoneId      time zone
     * @param padStart    whether to pad
     */
    public SecondSnowflakeIdStateParser(long epoch, int timestampBit, int machineBit, int sequenceBit, ZoneId zoneId, boolean padStart) {
        super(epoch, timestampBit, machineBit, sequenceBit, zoneId, padStart);
    }

    @Override
    protected DateTimeFormatter getDateTimeFormatter() {
        return DATE_TIME_FORMATTER;
    }

    @Override
    protected LocalDateTime getTimestamp(long diffTime) {
        return Instant.ofEpochSecond(epoch + diffTime).atZone(getZoneId()).toLocalDateTime();
    }

    @Override
    protected long getDiffTime(LocalDateTime timestamp) {
        return ZonedDateTime.of(timestamp, getZoneId()).toInstant().toEpochMilli() / 1000 - epoch;
    }

    public static SecondSnowflakeIdStateParser of(SnowflakeId snowflakeId) {
        return of(snowflakeId, ZoneId.systemDefault());
    }

    public static SecondSnowflakeIdStateParser of(SnowflakeId snowflakeId, ZoneId zoneId) {
        return of(snowflakeId, zoneId, false);
    }

    public static SecondSnowflakeIdStateParser of(SnowflakeId snowflakeId, ZoneId zoneId, boolean padStart) {
        return new SecondSnowflakeIdStateParser(snowflakeId.getEpoch(), snowflakeId.getTimestampBit(), snowflakeId.getMachineBit(), snowflakeId.getSequenceBit(), zoneId, padStart);
    }
}
