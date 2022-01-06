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

/**
 * @author ahoo wang
 */
public class PrefixIdConverter implements IdConverter {

    public static final String EMPTY_PREFIX = "";
    private final String prefix;
    private final IdConverter idConverter;

    public PrefixIdConverter(String prefix, IdConverter idConverter) {
        this.prefix = prefix;
        this.idConverter = idConverter;
    }

    public String getPrefix() {
        return prefix;
    }

    @Override
    public String asString(long id) {
        String idStr = idConverter.asString(id);
        if (EMPTY_PREFIX.equals(prefix)) {
            return idStr;
        }
        return prefix + idStr;
    }

    @Override
    public long asLong(String idString) {
        String idStr = idString.substring(prefix.length());
        return idConverter.asLong(idStr);
    }
}
