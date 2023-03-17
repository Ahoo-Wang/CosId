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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.stream.Stream;

class StandardLocalDateTimeConvertorTest {
    private static final ZoneId zoneId = ZoneId.of("Asia/Shanghai");
    private final StandardLocalDateTimeConvertor convertor =
        new StandardLocalDateTimeConvertor(zoneId, false, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    
    @ParameterizedTest
    @MethodSource("argsProvider")
    void toLocalDateTime(Comparable<?> shardingValue, LocalDateTime expected) {
        assertThat(convertor.toLocalDateTime(shardingValue), equalTo(expected));
    }
    
    static Stream<Arguments> argsProvider() {
        LocalDateTime localDateTime = LocalDateTime.of(2021, 12, 14, 22, 0);
        ZoneOffset zoneOffset = ZoneOffset.ofHours(8);
        return Stream.of(
            arguments(localDateTime, localDateTime),
            arguments(ZonedDateTime.of(localDateTime, zoneId), localDateTime),
            arguments(localDateTime.toInstant(zoneOffset), localDateTime),
            arguments(localDateTime.toLocalDate(), LocalDateTime.of(localDateTime.toLocalDate(), LocalTime.MIN)),
            arguments(new Date(localDateTime.toEpochSecond(zoneOffset) * 1000), localDateTime),
            arguments(localDateTime.toEpochSecond(zoneOffset) * 1000, localDateTime),
            arguments("2021-12-14 22:00:00", localDateTime),
            arguments(Year.of(2021), LocalDateTime.of(2021, 1, 1, 0, 0))
        );
    }
}