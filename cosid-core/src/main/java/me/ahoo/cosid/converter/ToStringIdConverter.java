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

import static me.ahoo.cosid.converter.RadixIdConverter.PAD_CHAR;

import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.stat.Stat;
import me.ahoo.cosid.stat.converter.ToStringConverterStat;

import com.google.common.base.Strings;

import javax.annotation.Nonnull;

/**
 * ToString ID Converter.
 *
 * @author ahoo wang
 */
public class ToStringIdConverter implements IdConverter {

    public static final ToStringIdConverter INSTANCE = new ToStringIdConverter(false, 0);
    private final boolean padStart;
    private final int charSize;

    public ToStringIdConverter(boolean padStart, int charSize) {
        this.padStart = padStart;
        this.charSize = charSize;
    }

    @Nonnull
    @Override
    public String asString(long id) {
        String idStr = String.valueOf(id);
        if (!padStart) {
            return idStr;
        }
        return Strings.padStart(idStr, charSize, PAD_CHAR);
    }

    @Override
    public long asLong(@Nonnull String idString) {
        return Long.parseLong(idString);
    }

    @Override
    public Stat stat() {
        return new ToStringConverterStat(getClass().getSimpleName(), padStart, charSize);
    }
}
