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
import me.ahoo.cosid.segment.grouped.GroupedAccessor;

import com.google.common.base.Preconditions;
import me.ahoo.cosid.stat.Stat;
import me.ahoo.cosid.stat.converter.GroupedPrefixConverterStat;

import javax.annotation.Nonnull;

public class GroupedPrefixIdConverter implements IdConverter, Decorator<IdConverter> {
    public static final String DEFAULT_DELIMITER = "-";
    private final String delimiter;
    private final IdConverter actual;

    public GroupedPrefixIdConverter(String delimiter, IdConverter actual) {
        Preconditions.checkNotNull(delimiter, "prefix can not be null!");
        this.delimiter = delimiter;
        this.actual = actual;
    }

    @Nonnull
    @Override
    public IdConverter getActual() {
        return actual;
    }

    public String getDelimiter() {
        return delimiter;
    }

    @Nonnull
    @Override
    public String asString(long id) {
        String idStr = actual.asString(id);
        String groupKey = GroupedAccessor.requiredGet().getKey();
        if (delimiter.isEmpty()) {
            return groupKey + idStr;
        }
        return groupKey + delimiter + idStr;
    }

    @Override
    public long asLong(@Nonnull String idString) {
        throw new UnsupportedOperationException("GroupedPrefixIdConverter does not support converting String to Long!");
    }

    @Override
    public Stat stat() {
        return new GroupedPrefixConverterStat(getClass().getSimpleName(), delimiter, actual.stat());
    }
}
