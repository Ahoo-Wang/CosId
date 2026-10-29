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

package me.ahoo.cosid.server;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.spring.autoconfigure.CosIdGeneratorRegistrar;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.StoreProvider;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Ordered;

import java.util.Set;

/**
 * Hands the server's own {@link BackingStore} to the runtime for the same store (R1-server-03), so the id endpoints and
 * the {@code cosid} endpoint do not open a second client (or, for JDBC, a second, unpooled data source) on it. Other
 * stores of the same type open as usual. It wins over the other providers of its type; the backing store skips it.
 */
final class SharedStoreProvider implements StoreProvider, Ordered {
    private final String type;
    private final String name;
    private final ObjectProvider<BackingStore> backing;
    private final ObjectProvider<StoreProvider> providers;
    private final ConfigurableListableBeanFactory beanFactory;

    SharedStoreProvider(String type, String name, ObjectProvider<BackingStore> backing, ObjectProvider<StoreProvider> providers,
                        ConfigurableListableBeanFactory beanFactory) {
        this.type = type;
        this.name = name;
        this.backing = backing;
        this.providers = providers;
        this.beanFactory = beanFactory;
    }

    @Override
    public String type() {
        return type;
    }

    /** The options of the provider of the type it shares: the same store options apply. */
    @Override
    public Set<String> options() {
        return BackingStore.provider(type, providers.orderedStream().filter(provider -> !(provider instanceof SharedStoreProvider)).toList())
            .map(StoreProvider::options).orElse(Set.of());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Store open(StoreConfig config) {
        if (!name.equals(config.name())) {
            return BackingStore.provider(type, providers.orderedStream().filter(provider -> !(provider instanceof SharedStoreProvider)).toList())
                .orElseThrow(() -> new CosIdException("No StoreProvider of type [" + type + "] for the store [" + config.name() + "]"))
                .open(config);
        }
        BackingStore store = backing.getObject();
        // The runtime uses the store until it closes: it goes first.
        beanFactory.registerDependentBean(CosIdServer.STORE_BEAN, CosIdGeneratorRegistrar.RUNTIME_BEAN);
        return new Store() {
            @Override
            public RangeStore ranges() {
                return store.ranges();
            }

            @Override
            public SlotLeaseStore slots() {
                return store.slots();
            }

            @Override
            public void close() {
                // The server closes it.
            }
        };
    }
}
