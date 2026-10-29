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

import me.ahoo.cosid.time.IdParts;

/**
 * Generates ids as their parts, for layouts wider than a {@code long} such as the 80-bit CosId; implemented by
 * {@link me.ahoo.cosid.time.TimeBasedGenerator}, and the view of a {@code cosid80} generator.
 */
@FunctionalInterface
public interface PartsIdGenerator {
    /**
     * The parts of the next id; thread-safe, never the same parts twice.
     *
     * @return the parts
     */
    IdParts nextParts();
}
