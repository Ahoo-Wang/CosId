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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.NamespaceMismatchException;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * {@code cosid_namespace.slot_offset} on a table of 5.0.0-alpha.1, which lacks it: without {@code initSchema} the
 * store keeps serving offset 0; {@code initSchema} adds the column. On H2.
 */
class JdbcSlotOffsetColumnTest {
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final Duration TOLERANCE = Duration.ofSeconds(1);

    @Test
    void withoutInitSchemaAnAlpha1TableServesOffsetZeroOnly() throws SQLException {
        DriverManagerDataSource dataSource = newDataSource();
        alpha1Schema(dataSource, JdbcTables.DEFAULT);
        JdbcStore store = new JdbcStore(dataSource, JdbcDialects.H2, JdbcTables.DEFAULT, Duration.ofSeconds(5));

        assertInstanceOf(AcquireResult.Acquired.class, acquire(store, "old", 0), "the old row reads offset 0");
        assertInstanceOf(AcquireResult.Acquired.class, acquire(new JdbcStore(dataSource, JdbcDialects.H2, JdbcTables.DEFAULT, Duration.ofSeconds(5)), "old", 0),
            "another store compares the old row equal to offset 0");
        assertInstanceOf(AcquireResult.Acquired.class, acquire(store, "fresh", 0), "a new namespace is recorded without the column");
        assertEquals(4, limitOf(dataSource, "cosid_namespace", "fresh"));

        IllegalStateException fresh = assertThrows(IllegalStateException.class, () -> acquire(store, "offset", 3));
        assertTrue(fresh.getMessage().contains("alter table cosid_namespace add column slot_offset integer not null default 0"), fresh.getMessage());
        assertThrows(IllegalStateException.class, () -> acquire(store, "old", 3));
        try (Connection connection = dataSource.getConnection()) {
            assertFalse(Jdbc.columns(connection, "cosid_namespace").contains("slot_offset"), "the store does not alter the table itself");
            assertTrue(Jdbc.columns(connection, "COSID_NAMESPACE").contains("slot_limit"), "names compare ignoring case");
        }
    }

    @Test
    void initSchemaAddsTheColumnToAnAlpha1Table() throws SQLException {
        DriverManagerDataSource dataSource = newDataSource();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema legacy");
        }
        JdbcTables tables = new JdbcTables("legacy.cosid_range", "legacy.cosid_slot", "legacy.cosid_namespace");
        alpha1Schema(dataSource, tables);
        JdbcStore store = new JdbcStore(dataSource, JdbcDialects.H2, tables, Duration.ofSeconds(5));
        store.initSchema();
        store.initSchema();

        try (Connection connection = dataSource.getConnection()) {
            assertTrue(Jdbc.columns(connection, tables.namespace()).contains("slot_offset"), "the column is added, once");
            assertTrue(Jdbc.columns(connection, "other.cosid_namespace").isEmpty(), "a qualified name matches its own schema only");
        }
        assertEquals(0, offsetOf(dataSource, tables.namespace(), "old"), "the old row reads the column default");
        assertInstanceOf(AcquireResult.Acquired.class, acquire(store, "old", 0));
        NamespaceMismatchException mismatch = assertThrows(NamespaceMismatchException.class, () -> acquire(store, "old", 3));
        assertTrue(mismatch.getMessage().contains("first used with limit 4, tolerance 1000ms and slot offset 0, now 4, 1000ms and 3"), mismatch.getMessage());

        assertInstanceOf(AcquireResult.Acquired.class, acquire(store, "offset", 3));
        assertEquals(3, offsetOf(dataSource, tables.namespace(), "offset"));
        assertThrows(NamespaceMismatchException.class, () -> acquire(new JdbcStore(dataSource, JdbcDialects.H2, tables, Duration.ofSeconds(5)), "offset", 0));
    }

    @Test
    void aStoreThatFoundNoColumnSeesTheOneAnotherInitSchemaAdds() throws SQLException {
        DriverManagerDataSource dataSource = newDataSource();
        alpha1Schema(dataSource, JdbcTables.DEFAULT);
        JdbcStore store = new JdbcStore(dataSource, JdbcDialects.H2, JdbcTables.DEFAULT, Duration.ofSeconds(5));
        assertInstanceOf(AcquireResult.Acquired.class, acquire(store, "old", 0));
        assertThrows(IllegalStateException.class, () -> acquire(store, "offset", 3));

        new JdbcStore(dataSource, JdbcDialects.H2, JdbcTables.DEFAULT, Duration.ofSeconds(5)).initSchema();
        assertInstanceOf(AcquireResult.Acquired.class, acquire(store, "offset", 3));
        assertEquals(3, offsetOf(dataSource, "cosid_namespace", "offset"));
    }

    @Test
    void sqlServerAddsAColumnWithoutTheColumnKeyword() {
        assertEquals("alter table t add c integer not null default 0", JdbcDialects.SQL_SERVER.addColumn("t", "c", "integer not null default 0"));
        assertEquals("alter table t add column c integer not null default 0", JdbcDialects.POSTGRESQL.addColumn("t", "c", "integer not null default 0"));
    }

    private static DriverManagerDataSource newDataSource() {
        return new DriverManagerDataSource("jdbc:h2:mem:offset-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    }

    /**
     * The tables as 5.0.0-alpha.1 created them, {@code cosid_namespace} without {@code slot_offset}, with one namespace
     * {@code old} of limit 4 and tolerance 1s recorded.
     */
    private static void alpha1Schema(DriverManagerDataSource dataSource, JdbcTables tables) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String ddl : JdbcDialects.H2.schema(tables)) {
                if (!ddl.contains(tables.namespace())) {
                    statement.execute(ddl);
                }
            }
            statement.execute("create table " + tables.namespace()
                + " (namespace varchar(100) not null primary key, slot_limit integer not null, tolerance_ms bigint not null)");
            statement.execute("insert into " + tables.namespace() + " (namespace, slot_limit, tolerance_ms) values ('old', 4, 1000)");
        }
    }

    private static AcquireResult acquire(JdbcStore store, String namespace, int slotOffset) {
        return store.slots().acquire(namespace, new Owner("host", UUID.randomUUID()), 4, TTL, TOLERANCE, slotOffset, OptionalInt.empty());
    }

    private static int limitOf(DriverManagerDataSource dataSource, String table, String namespace) throws SQLException {
        return intOf(dataSource, "select slot_limit from " + table + " where namespace = '" + namespace + "'");
    }

    private static int offsetOf(DriverManagerDataSource dataSource, String table, String namespace) throws SQLException {
        return intOf(dataSource, "select slot_offset from " + table + " where namespace = '" + namespace + "'");
    }

    private static int intOf(DriverManagerDataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            assertTrue(rs.next(), sql);
            return rs.getInt(1);
        }
    }
}
