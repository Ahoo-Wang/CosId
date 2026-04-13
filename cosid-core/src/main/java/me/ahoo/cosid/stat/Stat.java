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

package me.ahoo.cosid.stat;

import org.jspecify.annotations.Nullable;

/**
 * Statistical information interface.
 */
public interface Stat {

    /**
     * Gets the kind/type of this stat.
     *
     * @return the kind
     */
    String getKind();

    /**
     * Gets the wrapped actual stat.
     *
     * @return the actual stat or null
     */
    @Nullable
    default Stat getActual() {
        return null;
    }

    /**
     * Creates a simple stat with the specified kind and actual.
     *
     * @param kind the kind
     * @param actual the actual stat
     * @return the stat
     */
    static Stat simple(String kind, @Nullable Stat actual) {
        return new SimpleStat(kind, actual);
    }

    /**
     * Creates a simple stat with the specified kind.
     *
     * @param kind the kind
     * @return the stat
     */
    static Stat simple(String kind) {
        return simple(kind, null);
    }
}
