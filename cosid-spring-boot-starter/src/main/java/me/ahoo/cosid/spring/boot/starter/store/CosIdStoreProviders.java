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

package me.ahoo.cosid.spring.boot.starter.store;

import com.google.common.base.Strings;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.PropertyResolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Picks the {@link CosIdStoreProvider} of a store type.
 *
 * @author ahoo wang
 * @since 3.4.0
 */
public final class CosIdStoreProviders {
    
    private CosIdStoreProviders() {
    }
    
    /**
     * The store type a distributor setting selects, see {@link StoreTypes}. Fails naming the property that 4.0 removed
     * when the {@code provider} property is still set.
     *
     * @param environment  the application's environment
     * @param typeKey      {@code cosid.machine.distributor.type} or {@code cosid.segment.distributor.type}
     * @param providerKey  the removed {@code provider} property next to it
     * @param type         the bound type
     * @param defaultType  the type when unset
     */
    public static String storeType(PropertyResolver environment, String typeKey, String providerKey, @Nullable String type, String defaultType) {
        String provider = environment.getProperty(providerKey);
        if (provider != null) {
            throw new IllegalStateException("[" + providerKey + "] was removed in 4.0: set [" + typeKey + "=" + provider.trim()
                + "] instead, the type names any store, also an application's CosIdStoreProvider.");
        }
        return StoreTypes.normalize(type, defaultType);
    }
    
    /**
     * Create with the first provider of {@code type} that supports it: the application's providers first, in their
     * order, then the built-in ones.
     *
     * @param what describes what is created, for the error message
     * @throws IllegalStateException if no provider of {@code type} creates it
     */
    public static <T> T create(String type, List<CosIdStoreProvider> providers, Function<CosIdStoreProvider, T> create, String what) {
        List<CosIdStoreProvider> candidates = new ArrayList<>();
        for (CosIdStoreProvider provider : providers) {
            if (StoreTypes.same(type, provider.getType())) {
                candidates.add(provider);
            }
        }
        candidates.sort(Comparator.<CosIdStoreProvider, Boolean>comparing(provider -> provider instanceof BuiltInStoreProvider)
            .thenComparing(AnnotationAwareOrderComparator.INSTANCE));
        for (CosIdStoreProvider candidate : candidates) {
            T created = create.apply(candidate);
            if (created != null) {
                return created;
            }
        }
        String available = providers.stream().map(CosIdStoreProvider::getType).distinct().sorted().collect(Collectors.joining(", "));
        throw new IllegalStateException(Strings.lenientFormat(
            "No CosIdStoreProvider of type [%s] provides the %s. Registered types: [%s]. Add the backend module of that type, "
                + "or define a CosIdStoreProvider bean for it.", type, what, available));
    }
}
