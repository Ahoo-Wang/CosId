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

package me.ahoo.cosid.converter;

import me.ahoo.cosid.Decorator;
import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.stat.Stat;
import me.ahoo.cosid.stat.converter.DatePrefixConverterStat;

import javax.annotation.Nonnull;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DatePrefixIdConverter implements IdConverter, Decorator<IdConverter> {
    private final String prefix;
    private final String pattern;
    private final DateTimeFormatter formatter;
    private final String delimiter;
    private final IdConverter actual;

    public DatePrefixIdConverter(String prefix, String pattern, String delimiter, IdConverter actual) {
        this.prefix = prefix;
        this.pattern = pattern;
        this.formatter = DateTimeFormatter.ofPattern(pattern);
        this.delimiter = delimiter;
        this.actual = actual;
    }


    @Nonnull
    @Override
    public String asString(long id) {
        return prefix + LocalDateTime.now().format(formatter) + delimiter + actual.asString(id);
    }

    @Override
    public long asLong(@Nonnull String idString) {
        int appendedLength = prefix.length() + pattern.length() + delimiter.length();
        String idStr = idString.substring(appendedLength);
        return actual.asLong(idStr);
    }

    @Nonnull
    @Override
    public IdConverter getActual() {
        return actual;
    }

    @Override
    public Stat stat() {
        return new DatePrefixConverterStat(getClass().getSimpleName(), prefix, formatter.toString(), actual.stat());
    }
}
