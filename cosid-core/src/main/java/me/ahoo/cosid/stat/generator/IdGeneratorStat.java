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

package me.ahoo.cosid.stat.generator;

import me.ahoo.cosid.stat.Stat;

import org.jspecify.annotations.Nullable;

/**
 * Statistical information for ID generators.
 */
public interface IdGeneratorStat extends Stat {
    @Nullable
    @Override
    default IdGeneratorStat getActual() {
        return null;
    }

    /**
     * Gets the converter stat.
     *
     * @return the converter stat or null
     */
    @Nullable
    Stat getConverter();

    /**
     * Creates a simple stat with the specified kind and converter.
     *
     * @param kind the kind
     * @param actual the actual stat
     * @param converter the converter stat
     * @return the stat
     */
    static IdGeneratorStat simple(String kind, @Nullable IdGeneratorStat actual, Stat converter) {
        return new SimpleIdGeneratorStat(kind, actual, converter);
    }

    /**
     * Creates a simple stat with the specified kind and converter.
     *
     * @param kind the kind
     * @param converter the converter stat
     * @return the stat
     */
    static IdGeneratorStat simple(String kind, Stat converter) {
        return simple(kind, null, converter);
    }
}
