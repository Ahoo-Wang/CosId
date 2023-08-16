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

import com.google.common.base.Preconditions;
import me.ahoo.cosid.stat.Stat;
import me.ahoo.cosid.stat.converter.SuffixConverterStat;

import javax.annotation.Nonnull;

/**
 * Suffix IdConverter .
 *
 * @author ahoo wang
 */
public class SuffixIdConverter implements IdConverter, Decorator<IdConverter> {
    private final String suffix;
    private final IdConverter actual;

    public SuffixIdConverter(String suffix, IdConverter actual) {
        Preconditions.checkNotNull(suffix, "suffix can not be null!");
        this.suffix = suffix;
        this.actual = actual;
    }

    @Nonnull
    @Override
    public IdConverter getActual() {
        return actual;
    }

    public String getSuffix() {
        return suffix;
    }

    @Nonnull
    @Override
    public String asString(long id) {
        String idStr = actual.asString(id);
        if (suffix.isEmpty()) {
            return idStr;
        }
        return idStr + suffix;
    }

    @Override
    public long asLong(@Nonnull String idString) {
        String idStr = idString.substring(0, idString.length() - suffix.length());
        return actual.asLong(idStr);
    }

    @Override
    public Stat stat() {
        return new SuffixConverterStat(getClass().getSimpleName(), suffix, actual.stat());
    }
}
