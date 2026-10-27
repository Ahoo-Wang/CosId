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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;

/**
 * Dialect detection, the ServiceLoader SPI and the per-dialect SQL that is the same on every database version.
 */
class JdbcDialectsTest {
    private static final ClassLoader LOADER = JdbcDialectsTest.class.getClassLoader();

    private static Connection connectionTo(String product, String version) {
        DatabaseMetaData metaData = (DatabaseMetaData) Proxy.newProxyInstance(LOADER, new Class<?>[] {DatabaseMetaData.class}, (proxy, method, args) ->
            switch (method.getName()) {
                case "getDatabaseProductName" -> product;
                case "getDatabaseProductVersion" -> version;
                default -> throw new UnsupportedOperationException(method.getName());
            });
        return (Connection) Proxy.newProxyInstance(LOADER, new Class<?>[] {Connection.class}, (proxy, method, args) -> {
            if ("getMetaData".equals(method.getName())) {
                return metaData;
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    @Test
    void detectsEachProduct() throws SQLException {
        assertSame(JdbcDialects.MYSQL, JdbcDialects.detect(connectionTo("MySQL", "8.0.36"), LOADER));
        assertSame(JdbcDialects.MARIADB, JdbcDialects.detect(connectionTo("MariaDB", "10.6.18-MariaDB"), LOADER));
        assertSame(JdbcDialects.MARIADB, JdbcDialects.detect(connectionTo("MySQL", "5.5.5-10.6.18-MariaDB"), LOADER), "MariaDB behind the MySQL driver");
        assertSame(JdbcDialects.POSTGRESQL, JdbcDialects.detect(connectionTo("PostgreSQL", "12.20"), LOADER));
        assertSame(JdbcDialects.SQL_SERVER, JdbcDialects.detect(connectionTo("Microsoft SQL Server", "15.00.4430"), LOADER));
        assertSame(JdbcDialects.H2, JdbcDialects.detect(connectionTo("H2", "2.4.240 (2025-09-22)"), LOADER));
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class, () -> JdbcDialects.detect(connectionTo("Oracle", "19c"), LOADER));
        assertTrue(unknown.getMessage().contains("Oracle"));
    }

    @Test
    void registeredDialectsComeFirstAndAreFoundByName() {
        List<JdbcDialect> available = JdbcDialects.available(LOADER);
        assertEquals(TestDialect.class, available.get(0).getClass(), "a ServiceLoader dialect is tried before the built-in ones");
        assertEquals(TestDialect.class, JdbcDialects.named("TEST", LOADER).orElseThrow().getClass());
        assertSame(JdbcDialects.SQL_SERVER, JdbcDialects.named("sqlserver", LOADER).orElseThrow());
        assertFalse(JdbcDialects.named("oracle", LOADER).isPresent());
    }

    @Test
    void candidateReadsLimitTheirRows() {
        String limit = JdbcDialects.MARIADB.firstRows("slot", "cosid_slot", "namespace = ?", "slot", 8);
        assertEquals("select slot from cosid_slot where namespace = ? order by slot limit 8", limit);
        assertEquals(limit, JdbcDialects.MYSQL.firstRows("slot", "cosid_slot", "namespace = ?", "slot", 8));
        assertEquals(limit, JdbcDialects.POSTGRESQL.firstRows("slot", "cosid_slot", "namespace = ?", "slot", 8));
        assertEquals(limit, JdbcDialects.H2.firstRows("slot", "cosid_slot", "namespace = ?", "slot", 8));
        assertEquals("select top 8 slot from cosid_slot where namespace = ? order by slot",
            JdbcDialects.SQL_SERVER.firstRows("slot", "cosid_slot", "namespace = ?", "slot", 8));
    }

    @Test
    void schemasAreRenamedToTheTables() {
        JdbcTables tables = new JdbcTables("a.r", "s_slot", "n");
        for (JdbcDialect dialect : List.of(JdbcDialects.MYSQL, JdbcDialects.MARIADB, JdbcDialects.POSTGRESQL, JdbcDialects.SQL_SERVER, JdbcDialects.H2)) {
            List<String> schema = dialect.schema(tables);
            assertEquals(3, schema.size(), dialect.name());
            String all = String.join("\n", schema);
            assertFalse(all.contains("cosid_"), () -> dialect.name() + " keeps a default name: " + all);
            assertTrue(all.contains("a.r") && all.contains("s_slot") && all.contains(" n"), () -> dialect.name() + ": " + all);
            assertFalse(all.contains(";"), "statements without their terminator");
        }
        assertEquals(JdbcDialects.MYSQL.schema(JdbcTables.DEFAULT), JdbcDialects.MARIADB.schema(JdbcTables.DEFAULT));
    }

    @Test
    void classifiesFailures() {
        JdbcDialect dialect = JdbcDialects.H2;
        assertTrue(dialect.isDuplicateKey(new SQLException("dup", "23505")));
        assertTrue(dialect.isDuplicateKey(new SQLException("dup", "23000", 1062)));
        assertFalse(dialect.isDuplicateKey(new SQLException("other")));
        assertTrue(dialect.isRetryable(new SQLException("deadlock", "40001", 1213)));
        assertTrue(dialect.isRetryable(new SQLException("deadlock", "40P01")));
        assertFalse(dialect.isRetryable(new SQLException("lock wait timeout", "HY000", 1205)));
        assertTrue(JdbcDialects.SQL_SERVER.isRetryable(new SQLException("deadlock victim", null, 1205)));
    }

    /** Registered in {@code META-INF/services}; supports no database, so detection passes it by. */
    public static final class TestDialect implements JdbcDialect {
        @Override
        public String name() {
            return "test";
        }

        @Override
        public boolean supports(DatabaseMetaData metaData) {
            return false;
        }

        @Override
        public List<String> schema(JdbcTables tables) {
            return List.of();
        }

        @Override
        public String nowMillis() {
            return "0";
        }

        @Override
        public java.util.OptionalLong reserve(JdbcSession session, String key, long size, long initialOffset) {
            return java.util.OptionalLong.empty();
        }

        @Override
        public void provisionSlots(JdbcSession session, String namespace, int limit) {
        }
    }
}
