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
import me.ahoo.cosid.segment.grouped.GroupedKey;
import me.ahoo.cosid.stat.Stat;
import me.ahoo.cosid.stat.converter.GroupedPrefixConverterStat;

import com.google.common.base.Preconditions;
import org.jspecify.annotations.NonNull;

/**
 * Converter that prepends a dynamic group key as prefix to string IDs.
 *
 * <p>The group key comes with the id: {@link #asString(long, GroupedKey)}, as called by
 * {@link me.ahoo.cosid.segment.SegmentId#generateGroupedAsString()} and the string segment generators.
 *
 * @author ahoo wang
 */
public class GroupedPrefixIdConverter implements IdConverter, Decorator<IdConverter> {
    /**
     * Default delimiter between group key and ID.
     */
    public static final String DEFAULT_DELIMITER = "-";
    private final String delimiter;
    private final IdConverter actual;

    /**
     * Creates a grouped prefix converter.
     *
     * @param delimiter the delimiter between group key and ID
     * @param actual   the underlying converter
     */
    public GroupedPrefixIdConverter(String delimiter, IdConverter actual) {
        Preconditions.checkNotNull(delimiter, "prefix can not be null!");
        this.delimiter = delimiter;
        this.actual = actual;
    }

    @Override
    public @NonNull IdConverter getActual() {
        return actual;
    }

    /**
     * Gets the delimiter.
     *
     * @return the delimiter
     */
    public String getDelimiter() {
        return delimiter;
    }

    /**
     * Not supported: the group is not known from the id alone.
     *
     * @throws UnsupportedOperationException always; use {@link #asString(long, GroupedKey)}
     */
    @Override
    public @NonNull String asString(long id) {
        throw new UnsupportedOperationException("GroupedPrefixIdConverter needs the group of the id: use asString(id, group), "
            + "SegmentId.generateGroupedAsString(), or a string segment id with a grouped segment distributor.");
    }
    
    /**
     * Prefix the id with the key of {@code group}.
     */
    @Override
    public @NonNull String asString(long id, @NonNull GroupedKey group) {
        if (GroupedKey.NEVER.equals(group)) {
            throw new IllegalArgumentException("GroupedPrefixIdConverter needs a grouped id, but id [" + id + "] has no group: "
                + "use it with a grouped segment distributor.");
        }
        return withGroup(actual.asString(id, group), group);
    }
    
    private String withGroup(String idStr, GroupedKey group) {
        String groupKey = group.getKey();
        if (delimiter.isEmpty()) {
            return groupKey + idStr;
        }
        return groupKey + delimiter + idStr;
    }

    @Override
    public long asLong(@NonNull String idString) {
        throw new UnsupportedOperationException("GroupedPrefixIdConverter does not support converting String to Long!");
    }

    @Override
    public Stat stat() {
        return new GroupedPrefixConverterStat(getClass().getSimpleName(), delimiter, actual.stat());
    }
}
