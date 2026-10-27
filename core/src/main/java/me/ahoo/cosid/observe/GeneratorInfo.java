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

package me.ahoo.cosid.observe;

import java.util.Map;
import java.util.Objects;

/**
 * What {@code CosIdRuntime.describe()} tells about a generator; replaces the 4.x {@code Stat} types.
 *
 * @param name     the generator name
 * @param strategy {@code snowflake}, {@code cosid80}, {@code segment} or {@code periodic-segment}
 * @param store    the store name, empty for time-based generators
 * @param details  layout, slot, window, ready range headroom, batch size and the like
 */
public record GeneratorInfo(String name, String strategy, String store, Map<String, Object> details) {
    public GeneratorInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(store, "store");
        details = Map.copyOf(Objects.requireNonNull(details, "details"));
    }
}
