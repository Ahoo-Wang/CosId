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
import me.ahoo.cosid.stat.converter.PrefixConverterStat;

import jakarta.annotation.Nonnull;
import com.google.common.base.Preconditions;


/**
 * Converter for setting string ID prefix.
 *
 * @author ahoo wang
 */
public class PrefixIdConverter implements IdConverter, Decorator<IdConverter> {
    
    private final String prefix;
    private final IdConverter actual;
    
    public PrefixIdConverter(String prefix, IdConverter actual) {
        Preconditions.checkNotNull(prefix, "prefix can not be null!");
        this.prefix = prefix;
        this.actual = actual;
    }
    
    @Nonnull
    @Override
    public IdConverter getActual() {
        return actual;
    }
    
    public String getPrefix() {
        return prefix;
    }
    
    @Nonnull
    @Override
    public String asString(long id) {
        String idStr = actual.asString(id);
        if (prefix.isEmpty()) {
            return idStr;
        }
        return prefix + idStr;
    }
    
    @Override
    public long asLong(@Nonnull String idString) {
        String idStr = idString.substring(prefix.length());
        return actual.asLong(idStr);
    }
    
    @Override
    public Stat stat() {
        return new PrefixConverterStat(getClass().getSimpleName(), prefix, actual.stat());
    }
}
