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
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
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
        StoreConfig.Builder config = StoreConfig.builder("test", "jdbc").timeout(Duration.ofSeconds(3)).options(options);
        client.ifPresent(config::client);
        return config.build();
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

    @Test
    void usesACustomDialectNamedInTheOptionsAndItsStoreClock() throws SQLException {
        DataSource dataSource = new DriverManagerDataSource(newUrl(), "sa", "");
        Duration ttl = Duration.ofSeconds(30);
        try (Store store = new JdbcStoreProvider().open(config(Map.of("dialect", "Fixed-Clock-H2", "initSchema", "true"), Optional.of(dataSource)))) {
            assertInstanceOf(FixedClockH2Dialect.class, ((JdbcStore) store).dialect(), "the dialect registered with ServiceLoader, by name");
            assertEquals(new Range(7, 12), store.ranges().reserve("custom/gen", 5, 7));
            AcquireResult.Acquired acquired = assertInstanceOf(AcquireResult.Acquired.class,
                store.slots().acquire("custom", new Owner("host", UUID.randomUUID()), 2, ttl, Duration.ofSeconds(1), OptionalInt.empty()));
            assertEquals(FixedClockH2Dialect.NOW, acquired.lease().storeNow(), "the lease is stamped by the dialect's store clock");
            assertEquals(FixedClockH2Dialect.NOW + ttl.toMillis(), acquired.lease().expiresAt());
            assertEquals(FixedClockH2Dialect.NOW, store.slots().renew(acquired.lease(), ttl, Duration.ofSeconds(1)).orElseThrow().storeNow(),
                "renewals are stamped by it too");
        }
        try (Connection connection = dataSource.getConnection();
             ResultSet rs = connection.createStatement().executeQuery("select expires_at from cosid_slot where namespace = 'custom' and owner_key is not null")) {
            rs.next();
            assertEquals(FixedClockH2Dialect.NOW + ttl.toMillis(), rs.getLong(1), "the row is written with the dialect's clock");
        }
    }

    /**
     * A custom dialect, registered in {@code META-INF/services}: H2's SQL with a store clock of its own, frozen at
     * {@link #NOW}. It supports no database, so detection passes it by; only {@code options.dialect} selects it.
     */
    public static final class FixedClockH2Dialect implements JdbcDialect {
        /** The store clock: 2100-01-01T00:00:00Z, far from the H2 clock. */
        static final long NOW = 4_102_444_800_000L;

        private final JdbcDialect h2 = JdbcDialects.H2;

        @Override
        public String name() {
            return "fixed-clock-h2";
        }

        @Override
        public boolean supports(DatabaseMetaData metaData) {
            return false;
        }

        @Override
        public List<String> schema(JdbcTables tables) {
            return h2.schema(tables);
        }

        @Override
        public String nowMillis() {
            return "cast(" + NOW + " as bigint)";
        }

        @Override
        public OptionalLong reserve(JdbcSession session, String key, long size, long initialOffset) throws SQLException {
            return h2.reserve(session, key, size, initialOffset);
        }

        @Override
        public String firstRows(String columns, String table, String where, String orderBy, int rows) {
            return h2.firstRows(columns, table, where, orderBy, rows);
        }

        @Override
        public void provisionSlots(JdbcSession session, String namespace, int limit) throws SQLException {
            h2.provisionSlots(session, namespace, limit);
        }
    }
}
