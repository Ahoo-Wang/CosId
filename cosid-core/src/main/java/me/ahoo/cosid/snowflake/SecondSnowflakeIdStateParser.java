/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

import static java.time.temporal.ChronoField.*;

/**
 * @author ahoo wang
 * Creation time :2020/9/23 11:21
 */
public class SecondSnowflakeIdStateParser extends SnowflakeIdStateParser {

    public static final DateTimeFormatter DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(YEAR, 4)
            .appendValue(MONTH_OF_YEAR, 2)
            .appendValue(DAY_OF_MONTH, 2)
            .appendValue(HOUR_OF_DAY, 2)
            .appendValue(MINUTE_OF_HOUR, 2)
            .appendValue(SECOND_OF_MINUTE, 2)
            .toFormatter();

    public SecondSnowflakeIdStateParser(long epoch, int timestampBit, int machineBit, int sequenceBit) {
        this(epoch, timestampBit, machineBit, sequenceBit, ZoneId.systemDefault());
    }

    public SecondSnowflakeIdStateParser(long epoch, int timestampBit, int machineBit, int sequenceBit, ZoneId zoneId) {
        super(epoch, timestampBit, machineBit, sequenceBit, zoneId);
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
        return new SecondSnowflakeIdStateParser(snowflakeId.getEpoch(), snowflakeId.getTimestampBit(), snowflakeId.getMachineBit(), snowflakeId.getSequenceBit(), zoneId);
    }
}
