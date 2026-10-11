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

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeIdStateParser;
import me.ahoo.cosid.snowflake.SnowflakeIdStateParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;

class SnowflakeLocalDateTimeConvertorTest {
    private static final LocalDateTime EXPECTED = LocalDateTime.of(2023, 3, 17, 12, 34, 56, 789_000_000);
    private static final SnowflakeIdStateParser PARSER = new MillisecondSnowflakeIdStateParser(
        CosId.COSID_EPOCH,
        MillisecondSnowflakeId.DEFAULT_TIMESTAMP_BIT,
        MillisecondSnowflakeId.DEFAULT_MACHINE_BIT,
        MillisecondSnowflakeId.DEFAULT_SEQUENCE_BIT,
        ZoneOffset.UTC,
        false
    );
    private static final long SNOWFLAKE_ID = PARSER.parse("20230317123456789-1-2").getId();

    private final SnowflakeLocalDateTimeConvertor convertor = new SnowflakeLocalDateTimeConvertor(PARSER);

    static Stream<Arguments> supportedSnowflakeValues() {
        return Stream.of(
            arguments(SNOWFLAKE_ID),
            arguments(Radix62IdConverter.PAD_START.asString(SNOWFLAKE_ID))
        );
    }

    @ParameterizedTest
    @MethodSource("supportedSnowflakeValues")
    void toLocalDateTimeShouldParseTimestampFromLongOrRadix62String(Comparable<?> snowflakeValue) {
        assertEquals(EXPECTED, convertor.toLocalDateTime(snowflakeValue));
    }

    @Test
    void toLocalDateTimeShouldRejectUnsupportedValueType() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> convertor.toLocalDateTime(1));

        assertEquals("Unsupported sharding value type `1`.", error.getMessage());
    }
}
