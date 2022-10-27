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

/**
 * Suffix IdConverter .
 *
 * @author ahoo wang
 */
public class SuffixIdConverter implements IdConverter {
    private final String suffix;
    private final IdConverter idConverter;
    
    public SuffixIdConverter(String suffix, IdConverter idConverter) {
        Preconditions.checkNotNull(suffix, "suffix can not be null!");
        this.suffix = suffix;
        this.idConverter = idConverter;
    }
    
    public String getSuffix() {
        return suffix;
    }
    
    @Nonnull
    @Override
    public String asString(long id) {
        String idStr = idConverter.asString(id);
        if (suffix.isEmpty()) {
            return idStr;
        }
        return idStr + suffix;
    }
    
    @Override
    public long asLong(@Nonnull String idString) {
        String idStr = idString.substring(0, idString.length() - suffix.length());
        return idConverter.asLong(idStr);
    }
}
