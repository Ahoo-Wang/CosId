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
import me.ahoo.cosid.internal.Checks;
import me.ahoo.cosid.segment.grouped.GroupedKey;
import me.ahoo.cosid.stat.Stat;
import me.ahoo.cosid.stat.converter.PrefixConverterStat;

import org.jspecify.annotations.NonNull;


/**
 * Converter that prepends a fixed prefix to string IDs.
 *
 * @author ahoo wang
 */
public class PrefixIdConverter implements IdConverter, Decorator<IdConverter> {

    private final String prefix;
    private final IdConverter actual;

    /**
     * Creates a prefix converter.
     *
     * @param prefix the prefix to prepend
     * @param actual the underlying converter
     */
    public PrefixIdConverter(String prefix, IdConverter actual) {
        Checks.checkNotNull(prefix, "prefix can not be null!");
        this.prefix = prefix;
        this.actual = actual;
    }

    @Override
    public @NonNull IdConverter getActual() {
        return actual;
    }

    /**
     * Gets the prefix.
     *
     * @return the prefix
     */
    public String getPrefix() {
        return prefix;
    }

    @Override
    public @NonNull String asString(long id) {
        return withPrefix(actual.asString(id));
    }
    
    @Override
    public @NonNull String asString(long id, @NonNull GroupedKey group) {
        return withPrefix(actual.asString(id, group));
    }
    
    private String withPrefix(String idStr) {
        if (prefix.isEmpty()) {
            return idStr;
        }
        return prefix + idStr;
    }

    @Override
    public long asLong(@NonNull String idString) {
        Checks.checkArgument(idString.startsWith(prefix), "idString:[%s] must start with prefix:[%s]!", idString, prefix);
        String idStr = idString.substring(prefix.length());
        return actual.asLong(idStr);
    }

    @Override
    public Stat stat() {
        return new PrefixConverterStat(getClass().getSimpleName(), prefix, actual.stat());
    }
}
