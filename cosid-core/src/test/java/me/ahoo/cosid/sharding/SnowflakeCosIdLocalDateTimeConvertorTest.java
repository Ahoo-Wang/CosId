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

import me.ahoo.cosid.snowflake.MillisecondSnowflakeIdStateParser;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.stream.Stream;

class SnowflakeCosIdLocalDateTimeConvertorTest {
    private final SnowflakeCosIdLocalDateTimeConvertor convertor = new SnowflakeCosIdLocalDateTimeConvertor(MillisecondSnowflakeIdStateParser.INSTANCE);
    
    @ParameterizedTest
    @MethodSource("argsProvider")
    void toLocalDateTime(Comparable<?> shardingValue, LocalDate expected) {
        assertThat(convertor.toLocalDateTime(shardingValue).toLocalDate(), equalTo(expected));
    }
    
    static Stream<Arguments> argsProvider() {
        LocalDate localDate = LocalDate.of(2023, 3, 17);
        return Stream.of(
            arguments(427238791191728130L, localDate),
            arguments("0VYl5qVAX6h", localDate)
        );
    }
}