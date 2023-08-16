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

import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.stat.Stat;
import me.ahoo.cosid.stat.Statistical;
import me.ahoo.cosid.stat.generator.IdGeneratorStat;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Id Generator.
 *
 * @author ahoo wang
 */
@ThreadSafe
public interface IdGenerator extends StringIdGenerator, Statistical {
    
    /**
     * ID converter, used to convert {@code long} type ID to {@link String}.
     *
     * @return ID converter
     */
    @Nonnull
    default IdConverter idConverter() {
        return Radix62IdConverter.PAD_START;
    }
    
    /**
     * Generate distributed ID.
     *
     * @return generated distributed ID
     */
    long generate();
    
    @Nonnull
    @Override
    default String generateAsString() {
        return idConverter().asString(generate());
    }
    
    @Override
    default Stat stat() {
        return IdGeneratorStat.simple(getClass().getSimpleName(), idConverter().stat());
    }
}
