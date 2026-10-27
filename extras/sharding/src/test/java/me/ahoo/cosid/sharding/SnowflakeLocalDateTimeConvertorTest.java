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

import me.ahoo.cosid.codec.Radix;
import me.ahoo.cosid.time.Layout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;

class SnowflakeLocalDateTimeConvertorTest {
    private static final LocalDateTime EXPECTED = LocalDateTime.of(2023, 3, 17, 12, 34, 56, 789_000_000);
    private static final Layout LAYOUT = Layout.SNOWFLAKE_MS;
    private static final long SNOWFLAKE_ID = LAYOUT.pack(
        Duration.between(LAYOUT.epoch(), EXPECTED.toInstant(ZoneOffset.UTC)).toMillis(), 1, 2);

    private final SnowflakeLocalDateTimeConvertor convertor = new SnowflakeLocalDateTimeConvertor(LAYOUT, ZoneOffset.UTC);

    static Stream<Arguments> supportedSnowflakeValues() {
        return Stream.of(
            arguments(SNOWFLAKE_ID),
            arguments(SnowflakeLocalDateTimeConvertor.RADIX62_PAD_START.encode(SNOWFLAKE_ID))
        );
    }

    @ParameterizedTest
    @MethodSource("supportedSnowflakeValues")
    void toLocalDateTimeShouldParseTimestampFromLongOrRadix62String(Comparable<?> snowflakeValue) {
        assertEquals(EXPECTED, convertor.toLocalDateTime(snowflakeValue));
    }

    @Test
    void toLocalDateTimeShouldUseTheGivenZone() {
        SnowflakeLocalDateTimeConvertor shanghai = new SnowflakeLocalDateTimeConvertor(LAYOUT, ZoneOffset.ofHours(8));
        assertEquals(EXPECTED.plusHours(8), shanghai.toLocalDateTime(SNOWFLAKE_ID));
    }

    @Test
    void toLocalDateTimeShouldDecodeTextWithTheGivenCodec() {
        SnowflakeLocalDateTimeConvertor decimal = new SnowflakeLocalDateTimeConvertor(LAYOUT, ZoneOffset.UTC, Radix.of(10, 19, false));
        assertEquals(EXPECTED, decimal.toLocalDateTime(Long.toString(SNOWFLAKE_ID)));
    }

    @Test
    void toLocalDateTimeShouldHonorSecondLayouts() {
        Layout seconds = Layout.SNOWFLAKE_S;
        long id = seconds.pack(Duration.between(seconds.epoch(), EXPECTED.toInstant(ZoneOffset.UTC)).toSeconds(), 3, 4);
        assertEquals(EXPECTED.withNano(0), new SnowflakeLocalDateTimeConvertor(seconds, ZoneOffset.UTC).toLocalDateTime(id));
    }

    @Test
    void toLocalDateTimeShouldRejectUnsupportedValueType() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> convertor.toLocalDateTime(1));

        assertEquals("Unsupported sharding value type `1`.", error.getMessage());
    }
}
