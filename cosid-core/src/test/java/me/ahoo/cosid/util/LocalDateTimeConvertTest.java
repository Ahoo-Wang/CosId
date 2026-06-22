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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.stream.Stream;

class LocalDateTimeConvertTest {
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final ZoneOffset ZONE_OFFSET = ZoneOffset.ofHours(8);
    private static final LocalDateTime EXPECTED = LocalDateTime.of(2021, 12, 14, 22, 0);
    private static final Instant INSTANT = EXPECTED.toInstant(ZONE_OFFSET);
    private static final long EPOCH_MILLIS = INSTANT.toEpochMilli();
    private static final long EPOCH_SECONDS = INSTANT.getEpochSecond();

    static Stream<Arguments> instantLikeValues() {
        return Stream.of(
            arguments(Date.from(INSTANT), LocalDateTimeConvert.fromDate(Date.from(INSTANT), ZONE_ID)),
            arguments(EPOCH_MILLIS, LocalDateTimeConvert.fromTimestamp(EPOCH_MILLIS, ZONE_ID)),
            arguments(EPOCH_SECONDS, LocalDateTimeConvert.fromTimestampSecond(EPOCH_SECONDS, ZONE_ID)),
            arguments(INSTANT, LocalDateTimeConvert.fromInstant(INSTANT, ZONE_ID))
        );
    }

    @ParameterizedTest
    @MethodSource("instantLikeValues")
    void instantBasedConversionsShouldUseGivenZone(Object ignored, LocalDateTime actual) {
        assertEquals(EXPECTED, actual);
    }

    static Stream<Arguments> stringValues() {
        return Stream.of(
            arguments("yyyy-MM-dd HH:mm:ss", "2021-12-14 22:00:00", EXPECTED),
            arguments("yyyy-MM-dd HH:mm", "2021-12-14 22:00", EXPECTED),
            arguments("yyyy-MM-dd HH", "2021-12-14 22", EXPECTED),
            arguments("yyyy-MM-dd", "2021-12-14", LocalDateTime.of(2021, 12, 14, 0, 0))
        );
    }

    @ParameterizedTest
    @MethodSource("stringValues")
    void fromStringShouldParseLocalDateTimeOrPromoteLocalDateToStartOfDay(String pattern, String value, LocalDateTime expected) {
        assertEquals(expected, LocalDateTimeConvert.fromString(value, DateTimeFormatter.ofPattern(pattern)));
    }
}
