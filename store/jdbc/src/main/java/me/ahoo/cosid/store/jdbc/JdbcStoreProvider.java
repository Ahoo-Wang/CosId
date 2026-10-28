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

package me.ahoo.cosid.store.jdbc;

import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.StoreProvider;

import java.util.Map;
import javax.sql.DataSource;

/**
 * Opens {@link JdbcStore}s, type {@code jdbc} (06-stores.md §1), registered with {@link java.util.ServiceLoader}.
 *
 * <p>The client is a {@link DataSource}; without one the provider connects through {@link java.sql.DriverManager}
 * with {@code options.url}, {@code username} and {@code password}, without a pool. Further options:
 * <ul>
 *     <li>{@code dialect}: the dialect name ({@code mysql}, {@code mariadb}, {@code postgresql}, {@code sqlserver},
 *     {@code h2} or one registered as a {@link JdbcDialect}); detected from the database by default;</li>
 *     <li>{@code initSchema}: create the tables that are missing, and the columns that tables of an earlier 5.0
 *     pre-release lack, false by default;</li>
 *     <li>{@code rangeTable}, {@code slotTable}, {@code namespaceTable}: the table names ({@link JdbcTables}).</li>
 * </ul>
 * {@code StoreConfig.timeout} is the {@code queryTimeout} of every statement.
 */
public final class JdbcStoreProvider implements StoreProvider {
    /** The store type. */
    public static final String TYPE = "jdbc";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Store open(StoreConfig config) {
        Map<String, Object> options = config.options();
        DataSource dataSource = config.client().map(client -> {
            if (client instanceof DataSource given) {
                return given;
            }
            throw new IllegalArgumentException("The client of JDBC store [" + config.name() + "] must be a javax.sql.DataSource, not " + client.getClass().getName());
        }).orElseGet(() -> new DriverManagerDataSource(required(config, "url"), text(options, "username"), text(options, "password")));
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader() == null ? JdbcStoreProvider.class.getClassLoader()
            : Thread.currentThread().getContextClassLoader();
        String dialectName = text(options, "dialect");
        JdbcDialect dialect = dialectName == null ? JdbcStore.detect(dataSource, classLoader)
            : JdbcDialects.named(dialectName, classLoader).orElseThrow(() -> new IllegalArgumentException(
                "Unknown JDBC dialect [" + dialectName + "] of store [" + config.name() + "]"));
        JdbcStore store = new JdbcStore(dataSource, dialect, JdbcTables.of(options), config.timeout());
        if (Boolean.parseBoolean(text(options, "initSchema"))) {
            store.initSchema();
        }
        return store;
    }

    private static String required(StoreConfig config, String name) {
        String value = text(config.options(), name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("JDBC store [" + config.name() + "] needs a DataSource client or options." + name);
        }
        return value;
    }

    private static String text(Map<String, Object> options, String name) {
        Object value = options.get(name);
        return value == null ? null : value.toString();
    }
}
