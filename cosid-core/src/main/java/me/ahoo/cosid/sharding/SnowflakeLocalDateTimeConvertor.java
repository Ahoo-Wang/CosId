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

import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.snowflake.SnowflakeIdStateParser;

import com.google.common.base.Strings;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@RequiredArgsConstructor
public class SnowflakeLocalDateTimeConvertor implements LocalDateTimeConvertor {
    private final SnowflakeIdStateParser snowflakeIdStateParser;
    
    @Override
    public LocalDateTime toLocalDateTime(final Comparable<?> value) {
        return snowflakeIdStateParser.parseTimestamp(convertToSnowflakeId(value));
    }
    
    private Long convertToSnowflakeId(final Comparable<?> value) {
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof String) {
            return Radix62IdConverter.PAD_START.asLong((String) value);
        }
        throw new IllegalArgumentException(Strings.lenientFormat("Unsupported sharding value type `%s`.", value));
    }
}
