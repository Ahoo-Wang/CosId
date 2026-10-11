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

package me.ahoo.cosid.sharding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.stream.Stream;

class StandardLocalDateTimeConvertorTest {
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final ZoneOffset ZONE_OFFSET = ZoneOffset.ofHours(8);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final LocalDateTime EXPECTED = LocalDateTime.of(2021, 12, 14, 22, 0);
    private static final long EPOCH_MILLIS = EXPECTED.toInstant(ZONE_OFFSET).toEpochMilli();
    private static final long EPOCH_SECONDS = EXPECTED.toInstant(ZONE_OFFSET).getEpochSecond();

    private final StandardLocalDateTimeConvertor millisecondConvertor = new StandardLocalDateTimeConvertor(ZONE_ID, false, FORMATTER);

    static Stream<Arguments> supportedValues() {
        Instant instant = EXPECTED.toInstant(ZONE_OFFSET);
        return Stream.of(
            arguments(EXPECTED, EXPECTED),
            arguments(ZonedDateTime.of(EXPECTED, ZONE_ID), EXPECTED),
            arguments(OffsetDateTime.of(EXPECTED, ZONE_OFFSET), EXPECTED),
            arguments(instant, EXPECTED),
            arguments(LocalDate.of(2021, 12, 14), LocalDateTime.of(LocalDate.of(2021, 12, 14), LocalTime.MIN)),
            arguments(Date.from(instant), EXPECTED),
            arguments(EPOCH_MILLIS, EXPECTED),
            arguments("2021-12-14 22:00:00", EXPECTED),
            arguments(YearMonth.of(2021, 12), LocalDateTime.of(2021, 12, 1, 0, 0)),
            arguments(Year.of(2021), LocalDateTime.of(2021, 1, 1, 0, 0))
        );
    }

    @ParameterizedTest
    @MethodSource("supportedValues")
    void toLocalDateTimeShouldConvertSupportedValueTypes(Comparable<?> value, LocalDateTime expected) {
        assertEquals(expected, millisecondConvertor.toLocalDateTime(value));
    }

    @Test
    void toLocalDateTimeShouldInterpretLongAsSecondsWhenConfigured() {
        StandardLocalDateTimeConvertor secondConvertor = new StandardLocalDateTimeConvertor(ZONE_ID, true, FORMATTER);

        assertEquals(EXPECTED, secondConvertor.toLocalDateTime(EPOCH_SECONDS));
    }

    @Test
    void toLocalDateTimeShouldRejectUnsupportedValueType() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> millisecondConvertor.toLocalDateTime(1));

        assertEquals("Unsupported sharding value type `1`.", error.getMessage());
    }
}
