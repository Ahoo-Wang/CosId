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

package me.ahoo.cosid.runtime;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * A store of a {@link RuntimeSpec}, declared once and referred to by name.
 *
 * @param type    the store type, matched to a {@code StoreProvider}
 * @param timeout the operation timeout
 * @param options backend options
 */
public record StoreSpec(String type, Duration timeout, Map<String, Object> options) {
    public StoreSpec {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(timeout, "timeout");
        options = Map.copyOf(Objects.requireNonNull(options, "options"));
    }
}
