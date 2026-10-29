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
import me.ahoo.cosid.runtime.StoreSpec;
import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.StoreOptions;
import me.ahoo.cosid.store.StoreProvider;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * The store whose primitives the server exposes (08-remote.md §1): one of {@code cosid.stores}, opened through the
 * same {@link StoreProvider}s as the runtime (beans first, then {@link ServiceLoader}). A JDBC store with
 * {@code options.url} gets a HikariCP pool sized to the blocking pool, which closes with the store.
 */
public final class BackingStore implements Store {
    private static final Logger log = LoggerFactory.getLogger(BackingStore.class);

    private final String name;
    private final Store store;
    private final AutoCloseable ownedClient;

    BackingStore(String name, Store store, AutoCloseable ownedClient) {
        this.name = name;
        this.store = store;
        this.ownedClient = ownedClient;
    }

    /**
     * Opens the backing store.
     *
     * @param stores    {@code cosid.stores}
     * @param name      {@code cosid.server.store}; may be null when there is one store
     * @param providers the provider beans, which win over {@link ServiceLoader} for their type
     * @param poolSize  the connection pool size of a JDBC store
     * @return the store
     */
    static BackingStore open(Map<String, StoreSpec> stores, String name, List<StoreProvider> providers, int poolSize) {
        Map.Entry<String, StoreSpec> backing = choose(stores, name);
        String chosen = backing.getKey();
        StoreSpec spec = backing.getValue();
        StoreProvider provider = provider(spec.type(), providers.stream().filter(bean -> !(bean instanceof SharedStoreProvider)).toList())
            .orElseThrow(() -> new CosIdException("No StoreProvider of type [" + spec.type() + "] for the backing store"));
        // The options as the runtime passes them: an environment variable or kebab-case key reaches its option (R1-spring-01).
        Map<String, Object> options = StoreOptions.resolve(chosen, provider, spec.options());
        HikariDataSource pool = "jdbc".equals(spec.type()) && options.get("url") != null ? pool(chosen, options, poolSize) : null;
        try {
            StoreConfig.Builder config = StoreConfig.builder(chosen, spec.type()).timeout(spec.timeout()).options(options);
            if (pool != null) {
                config.client(pool);
            }
            Store store = provider.open(config.build());
            log.info("Backing store [{}] of type [{}] opened", chosen, spec.type());
            return new BackingStore(chosen, store, pool);
        } catch (RuntimeException e) {
            if (pool != null) {
                pool.close();
            }
            throw e;
        }
    }

    /**
     * The backing store among {@code cosid.stores}.
     *
     * @param stores {@code cosid.stores}
     * @param name   {@code cosid.server.store}; may be null when there is one store
     * @return its name and spec
     */
    static Map.Entry<String, StoreSpec> choose(Map<String, StoreSpec> stores, String name) {
        if (name == null || name.isBlank()) {
            if (stores.size() != 1) {
                throw new CosIdException(ServerProperties.PREFIX + ".store: name the backing store among cosid.stores " + stores.keySet());
            }
            return stores.entrySet().iterator().next();
        }
        StoreSpec spec = stores.get(name);
        if (spec == null) {
            throw new CosIdException(ServerProperties.PREFIX + ".store: no store [" + name + "] in cosid.stores " + stores.keySet());
        }
        return Map.entry(name, spec);
    }

    static Optional<StoreProvider> provider(String type, List<StoreProvider> beans) {
        for (StoreProvider provider : beans) {
            if (provider.type().equals(type)) {
                return Optional.of(provider);
            }
        }
        Map<String, StoreProvider> loaded = new HashMap<>();
        try {
            for (StoreProvider provider : ServiceLoader.load(StoreProvider.class)) {
                loaded.putIfAbsent(provider.type(), provider);
            }
        } catch (ServiceConfigurationError e) {
            throw new CosIdException("Failed to load the StoreProvider services", e);
        }
        return Optional.ofNullable(loaded.get(type));
    }

    private static HikariDataSource pool(String name, Map<String, Object> options, int poolSize) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("cosid-server-" + name);
        config.setJdbcUrl(options.get("url").toString());
        Optional.ofNullable(options.get("username")).map(Object::toString).ifPresent(config::setUsername);
        Optional.ofNullable(options.get("password")).map(Object::toString).ifPresent(config::setPassword);
        config.setMaximumPoolSize(poolSize);
        return new HikariDataSource(config);
    }

    /**
     * The name of the store in {@code cosid.stores}.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

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
        try {
            store.close();
        } finally {
            if (ownedClient != null) {
                try {
                    ownedClient.close();
                } catch (Exception e) {
                    log.warn("Closing the client of the backing store [{}] failed", name, e);
                }
            }
        }
    }
}
