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

package me.ahoo.cosid.util;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * @author ahoo wang
 */
class LocalDateTimeConvertTest {
    private final static ZoneId ZONE_ID_SHANGHAI = ZoneId.of("Asia/Shanghai");
    private final static ZoneOffset ZONE_OFFSET_SHANGHAI = ZoneOffset.of("+8");
    private final static LocalDateTime EXPECTED_DATE_TIME = LocalDateTime.of(2021, 12, 14, 22, 0);
    private final static long TEST_TIMESTAMP = EXPECTED_DATE_TIME.toInstant(ZONE_OFFSET_SHANGHAI).toEpochMilli();
    private final static long TEST_TIMESTAMP_SECOND = EXPECTED_DATE_TIME.toInstant(ZONE_OFFSET_SHANGHAI).getEpochSecond();

    @Test
    void fromDate() {
        Date date = new Date(TEST_TIMESTAMP);
        LocalDateTime actual = LocalDateTimeConvert.fromDate(date, ZONE_ID_SHANGHAI);
        Assertions.assertEquals(EXPECTED_DATE_TIME, actual);
    }

    @Test
    void fromTimestamp() {
        LocalDateTime actual = LocalDateTimeConvert.fromTimestamp(TEST_TIMESTAMP, ZONE_ID_SHANGHAI);
        Assertions.assertEquals(EXPECTED_DATE_TIME, actual);
    }

    @Test
    void fromTimestampSecond() {
        LocalDateTime actual = LocalDateTimeConvert.fromTimestampSecond(TEST_TIMESTAMP_SECOND, ZONE_ID_SHANGHAI);
        Assertions.assertEquals(EXPECTED_DATE_TIME, actual);
    }

    static Stream<Arguments> fromStringArgsProvider() {
        return Stream.of(
                arguments("yyyy-MM-dd HH:mm:ss", "2021-12-14 22:00:00", LocalDateTime.of(2021, 12, 14, 22, 0)),
                arguments("yyyy-MM-dd HH:mm", "2021-12-14 22:00", LocalDateTime.of(2021, 12, 14, 22, 0)),
                arguments("yyyy-MM-dd HH", "2021-12-14 22", LocalDateTime.of(2021, 12, 14, 22, 0)),
                arguments("yyyy-MM-dd", "2021-12-14", LocalDateTime.of(2021, 12, 14, 0, 0))
        );
    }

    @ParameterizedTest
    @MethodSource("fromStringArgsProvider")
    public void fromString(String dateTimePattern, String dateTime, LocalDateTime expected) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(dateTimePattern);
        LocalDateTime actual = LocalDateTimeConvert.fromString(dateTime, dateTimeFormatter);
        Assertions.assertEquals(expected, actual);
    }
}
