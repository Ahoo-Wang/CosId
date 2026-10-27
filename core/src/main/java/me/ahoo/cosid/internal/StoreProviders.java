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

package me.ahoo.cosid.internal;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.store.StoreProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Finds the {@link StoreProvider} of a store type (06-stores.md §1): the explicit providers (Spring beans or
 * {@code RuntimeOptions.storeProviders}) first, then those {@link ServiceLoader} finds. Both go through the same
 * creation path.
 */
public final class StoreProviders {
    private final Map<String, StoreProvider> explicit = new HashMap<>();
    private Map<String, StoreProvider> loaded;

    /**
     * The providers.
     *
     * @param explicit providers that win over {@link ServiceLoader} for their type; the first one wins within them
     */
    public StoreProviders(List<StoreProvider> explicit) {
        for (StoreProvider provider : explicit) {
            this.explicit.putIfAbsent(provider.type(), provider);
        }
    }

    /**
     * The provider of {@code type}.
     *
     * @param type the store type
     * @return the provider, or empty when none is known
     * @throws CosIdException if {@link ServiceLoader} fails to load a provider
     */
    public Optional<StoreProvider> find(String type) {
        StoreProvider provider = explicit.get(type);
        if (provider != null) {
            return Optional.of(provider);
        }
        return Optional.ofNullable(loaded().get(type));
    }

    private Map<String, StoreProvider> loaded() {
        if (loaded == null) {
            Map<String, StoreProvider> found = new HashMap<>();
            try {
                for (StoreProvider provider : ServiceLoader.load(StoreProvider.class)) {
                    found.putIfAbsent(provider.type(), provider);
                }
            } catch (ServiceConfigurationError e) {
                throw new CosIdException("Failed to load the StoreProvider services", e);
            }
            loaded = found;
        }
        return loaded;
    }
}
