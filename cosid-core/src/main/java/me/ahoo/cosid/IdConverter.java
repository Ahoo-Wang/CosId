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

package me.ahoo.cosid;

import me.ahoo.cosid.stat.Stat;
import me.ahoo.cosid.stat.Statistical;

import com.google.errorprone.annotations.ThreadSafe;
import jakarta.annotation.Nonnull;

/**
 * ID converter.
 *
 * @author ahoo wang
 */
@ThreadSafe
public interface IdConverter extends Statistical {
    
    /**
     * convert {@code long} type ID to {@link String}.
     *
     * @param id {@code long} type ID
     * @return {@link String} type ID
     */
    @Nonnull
    String asString(long id);
    
    /**
     * convert {@link String} type ID to {@code long}.
     *
     * @param idString {@link String} type ID
     * @return {@code long} type ID
     */
    long asLong(@Nonnull String idString);
    
    @Override
    default Stat stat() {
        return Stat.simple(getClass().getSimpleName());
    }
}
