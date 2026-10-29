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

import java.util.Set;

/**
 * Opens stores of one type (06-stores.md §1); found through {@link java.util.ServiceLoader} or given to the runtime
 * (as a Spring bean, say), both along the same path. Replaces 4.x {@code CosIdStoreProvider}.
 */
public interface StoreProvider {
    /**
     * The type this provider opens.
     *
     * @return {@code "jdbc"}, {@code "redis"}, {@code "mongo"}, {@code "zookeeper"}, {@code "remote"} or your own
     */
    String type();

    /**
     * The option keys this provider reads from {@link StoreConfig#options()}, spelled as it reads them (R1-spi-01).
     * The runtime matches each configured key to one of these ignoring case, {@code -} and {@code _}, as Spring's
     * relaxed binding does ({@code init-schema}, {@code INITSCHEMA} and {@code initSchema} are one key; {@code .} still
     * separates), passes it on under this spelling, and fails the start on any other key, listing these.
     *
     * @return the option keys; default none, so a provider that reads options declares them
     */
    default Set<String> options() {
        return Set.of();
    }

    /**
     * Opens a store.
     *
     * @param config the store, its options under the spelling of {@link #options()}
     * @return the store, never null (the runtime refuses a null at start); the runtime closes it
     * @throws IllegalArgumentException if an option or the client does not fit this store (R1-core-16)
     */
    Store open(StoreConfig config);
}
