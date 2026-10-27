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

package me.ahoo.cosid.store;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What a {@link StoreProvider} opens a store with (06-stores.md §1).
 *
 * @param name    the store name in the runtime spec
 * @param type    the store type
 * @param timeout the operation timeout, enforced by the backend's own mechanism
 * @param options backend options, such as {@code initSchema}
 * @param client  an existing connection object ({@code DataSource}, a Lettuce connection, {@code MongoClient},
 *                {@code CuratorFramework}); when empty the provider creates its own from the options and closes it
 */
public record StoreConfig(String name, String type, Duration timeout, Map<String, Object> options, Optional<Object> client) {
    public StoreConfig {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(timeout, "timeout");
        options = Map.copyOf(Objects.requireNonNull(options, "options"));
        Objects.requireNonNull(client, "client");
    }
}
