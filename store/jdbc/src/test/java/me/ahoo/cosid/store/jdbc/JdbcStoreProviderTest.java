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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.StoreProvider;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.ServiceLoader;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * {@link JdbcStoreProvider} on H2: the options of 06-stores.md §2.1.
 */
class JdbcStoreProviderTest {
    private static String newUrl() {
        return "jdbc:h2:mem:provider-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    }

    private static StoreConfig config(Map<String, Object> options, Optional<Object> client) {
        return new StoreConfig("test", "jdbc", Duration.ofSeconds(3), options, client);
    }

    @Test
    void isFoundByServiceLoader() {
        assertTrue(ServiceLoader.load(StoreProvider.class).stream().anyMatch(p -> p.get() instanceof JdbcStoreProvider && "jdbc".equals(p.get().type())));
    }

    @Test
    void opensFromTheUrlAndCreatesTheSchemaWhenAsked() {
        String url = newUrl();
        try (Store store = new JdbcStoreProvider().open(config(Map.of("url", url, "username", "sa", "password", "", "initSchema", true), Optional.empty()))) {
            JdbcStore jdbc = assertInstanceOf(JdbcStore.class, store);
            assertSame(JdbcDialects.H2, jdbc.dialect(), "detected");
            assertEquals(new Range(3, 13), store.ranges().reserve("ns/gen", 10, 3));
            assertInstanceOf(AcquireResult.Acquired.class,
                store.slots().acquire("ns", new Owner("host", UUID.randomUUID()), 4, Duration.ofSeconds(30), Duration.ofSeconds(1), OptionalInt.empty()));
        }
    }

    @Test
    void initSchemaIsOffByDefault() {
        String url = newUrl();
        try (Store store = new JdbcStoreProvider().open(config(Map.of("url", url, "username", "sa"), Optional.empty()))) {
            JdbcStoreException e = assertThrows(JdbcStoreException.class, () -> store.ranges().reserve("ns/gen", 10, 0));
            assertInstanceOf(SQLException.class, e.getCause());
        }
    }

    @Test
    void usesTheDataSourceClientTheNamedDialectAndTheTableNames() throws SQLException {
        DataSource dataSource = new DriverManagerDataSource(newUrl(), "sa", "");
        Map<String, Object> options = Map.of("dialect", "H2", "initSchema", "true", "rangeTable", "id_range", "slotTable", "id_slot",
            "namespaceTable", "id_namespace");
        try (Store store = new JdbcStoreProvider().open(config(options, Optional.of(dataSource)))) {
            assertEquals(new JdbcTables("id_range", "id_slot", "id_namespace"), ((JdbcStore) store).tables());
            assertEquals(new Range(0, 5), store.ranges().reserve("k", 5, 0));
            assertInstanceOf(AcquireResult.Acquired.class,
                store.slots().acquire("ns", new Owner("host", UUID.randomUUID()), 2, Duration.ofSeconds(30), Duration.ofSeconds(1), OptionalInt.empty()));
        }
        try (Connection connection = dataSource.getConnection(); ResultSet rs = connection.createStatement().executeQuery("select count(*) from id_slot")) {
            rs.next();
            assertEquals(2, rs.getInt(1), "the namespace's slots are provisioned in the configured table");
        }
    }

    @Test
    void rejectsBadConfigurations() {
        JdbcStoreProvider provider = new JdbcStoreProvider();
        assertThrows(IllegalArgumentException.class, () -> provider.open(config(Map.of(), Optional.empty())), "neither client nor url");
        assertThrows(IllegalArgumentException.class, () -> provider.open(config(Map.of(), Optional.of("not a data source"))));
        assertThrows(IllegalArgumentException.class, () -> provider.open(config(Map.of("url", newUrl(), "dialect", "oracle"), Optional.empty())));
        assertThrows(IllegalArgumentException.class, () -> provider.open(config(Map.of("url", newUrl(), "rangeTable", "x; drop table y"), Optional.empty())));
        assertThrows(JdbcStoreException.class, () -> provider.open(config(Map.of("url", "jdbc:nothing:here"), Optional.empty())), "no driver to detect with");
    }
}
