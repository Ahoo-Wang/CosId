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

import me.ahoo.cosid.IdConverter;

import com.google.common.base.Preconditions;

import javax.annotation.Nonnull;
import java.time.Year;

/**
 * Converter for setting string ID prefix.
 *
 * @author ahoo wang
 */
public class YearPrefixIdConverter implements IdConverter {
    
    private final String delimiter;
    private final IdConverter idConverter;
    
    public YearPrefixIdConverter(String delimiter, IdConverter idConverter) {
        Preconditions.checkNotNull(delimiter, "prefix can not be null!");
        this.delimiter = delimiter;
        this.idConverter = idConverter;
    }
    
    public String getDelimiter() {
        return delimiter;
    }
    
    @Nonnull
    @Override
    public String asString(long id) {
        String idStr = idConverter.asString(id);
        Year nowYear = Year.now();
        if (delimiter.isEmpty()) {
            return nowYear + idStr;
        }
        return nowYear + delimiter + idStr;
    }
    
    @Override
    public long asLong(@Nonnull String idString) {
        int beginIndex = delimiter.length() + 4;
        String idStr = idString.substring(beginIndex);
        return idConverter.asLong(idStr);
    }
}
