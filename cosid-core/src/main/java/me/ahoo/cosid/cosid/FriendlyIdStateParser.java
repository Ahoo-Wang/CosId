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

package me.ahoo.cosid.cosid;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MILLI_OF_SECOND;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;
import static me.ahoo.cosid.converter.RadixIdConverter.PAD_CHAR;
import static me.ahoo.cosid.snowflake.SnowflakeIdStateParser.DELIMITER;

import me.ahoo.cosid.converter.RadixIdConverter;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;

public class FriendlyIdStateParser implements CosIdIdStateParser {

    public static final int DECIMAL_RADIX = 10;
    private final ZoneId zoneId;
    private final boolean padStart;

    public static final DateTimeFormatter DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(YEAR, 4)
            .appendValue(MONTH_OF_YEAR, 2)
            .appendValue(DAY_OF_MONTH, 2)
            .appendValue(HOUR_OF_DAY, 2)
            .appendValue(MINUTE_OF_HOUR, 2)
            .appendValue(SECOND_OF_MINUTE, 2)
            .appendValue(MILLI_OF_SECOND, 3)
            .toFormatter();

    private final int machineCharSize;
    private final int sequenceCharSize;

    public FriendlyIdStateParser(ZoneId zoneId, boolean padStart, int machineBit, int sequenceBit) {
        this.zoneId = zoneId;
        this.padStart = padStart;
        this.machineCharSize = RadixIdConverter.maxCharSize(DECIMAL_RADIX, machineBit);
        this.sequenceCharSize = RadixIdConverter.maxCharSize(DECIMAL_RADIX, sequenceBit);
    }

    @Override
    public CosIdState asState(String id) {
        List<String> segments = Splitter.on(DELIMITER).trimResults().omitEmptyStrings().splitToList(id);
        if (segments.size() != 3) {
            throw new IllegalArgumentException(Strings.lenientFormat("id :[%s] Illegal.", id));
        }
        String timestampStr = segments.get(0);
        long timestamp = LocalDateTime.parse(timestampStr, DATE_TIME_FORMATTER).atZone(zoneId).toInstant().toEpochMilli();
        int machineId = Integer.parseInt(segments.get(1));
        int sequence = Integer.parseInt(segments.get(2));
        return new CosIdState(timestamp, machineId, sequence);
    }

    public static String intAsString(boolean padStart, int value, int charSize) {
        if (padStart) {
            return Strings.padStart(String.valueOf(value), charSize, PAD_CHAR);
        }
        return String.valueOf(value);
    }

    @Override
    public String asString(long lastTimestamp, int machineId, int sequence) {
        LocalDateTime timestamp = getTimestamp(lastTimestamp);
        return new StringBuilder(timestamp.format(DATE_TIME_FORMATTER))
                .append(DELIMITER)
                .append(intAsString(padStart, machineId, machineCharSize))
                .append(DELIMITER)
                .append(intAsString(padStart, sequence, sequenceCharSize))
                .toString();
    }

    private LocalDateTime getTimestamp(long lastTimestamp) {
        return Instant.ofEpochMilli(lastTimestamp).atZone(zoneId).toLocalDateTime();
    }
}
