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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.lease.AcquireReason;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * The slot table's index on {@code (namespace, last_owner_key)} (R1-jdbc-01) on every dialect: {@code initSchema}
 * adds it to a table of 5.0.0-alpha.1, once, and the query of an acquire's own rows can use it.
 */
abstract class SlotIndexTest {
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final Duration TOLERANCE = Duration.ofSeconds(1);

    abstract TestDatabase database();

    @BeforeEach
    void available() {
        database().assumeAvailable();
    }

    /** Tables of their own, so that the index is added here and not by an earlier test. */
    private static JdbcTables newTables() {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return new JdbcTables("ix_range_" + id, "ix_slot_" + id, "ix_namespace_" + id);
    }

    /** The tables as 5.0.0-alpha.1 created them: the schema without the index. */
    private void alpha1Schema(JdbcTables tables) throws SQLException {
        try (Connection connection = database().dataSource().getConnection(); Statement statement = connection.createStatement()) {
            for (String ddl : database().dialect().schema(tables)) {
                if (ddl.contains(tables.lastOwnerIndex()) && !ddl.contains("create table")) {
                    continue;
                }
                statement.execute(ddl.replaceAll(",\\s*index " + tables.lastOwnerIndex() + " \\(namespace, last_owner_key\\)", ""));
            }
        }
    }

    private boolean hasIndex(JdbcTables tables) throws SQLException {
        try (Connection connection = database().dataSource().getConnection()) {
            return Jdbc.hasIndex(connection, tables.slot(), JdbcStore.LAST_OWNER_INDEX_COLUMNS);
        }
    }

    @Test
    void initSchemaAddsTheIndexToAnAlpha1TableOnce() throws SQLException {
        JdbcTables tables = newTables();
        alpha1Schema(tables);
        assertFalse(hasIndex(tables), "a table of 5.0.0-alpha.1 has no index on (namespace, last_owner_key)");
        JdbcStore store = new JdbcStore(database().dataSource(), database().dialect(), tables, TestDatabase.TIMEOUT);
        store.initSchema();
        store.initSchema();
        assertTrue(hasIndex(tables), "initSchema adds it, and again changes nothing");

        // The candidates of an acquire find the owner key's rows through it: the idempotent retry and the sticky slot.
        Owner owner = new Owner("host-" + UUID.randomUUID(), UUID.randomUUID());
        AcquireResult first = store.slots().acquire("ix", owner, 64, TTL, TOLERANCE, 0, OptionalInt.empty());
        int slot = assertInstanceOf(AcquireResult.Acquired.class, first).lease().slot();
        AcquireResult.Acquired again = assertInstanceOf(AcquireResult.Acquired.class, store.slots().acquire("ix", owner, 64, TTL, TOLERANCE, 0,
            OptionalInt.empty()));
        assertTrue(again.lease().slot() == slot && again.reason() == AcquireReason.IDEMPOTENT, () -> "the same slot again: " + again);
        store.slots().release(again.lease(), again.lease().ceiling());
        Owner restarted = new Owner(owner.key(), UUID.randomUUID());
        AcquireResult.Acquired sticky = assertInstanceOf(AcquireResult.Acquired.class, store.slots().acquire("ix", restarted, 64, TTL, TOLERANCE, 0,
            OptionalInt.empty()));
        assertTrue(sticky.lease().slot() == slot && sticky.reason() == AcquireReason.STICKY, () -> "the slot it held last: " + sticky);
    }

    @Test
    void theQueryOfTheOwnersRowsCanUseTheIndex() throws SQLException {
        JdbcTables tables = newTables();
        new JdbcStore(database().dataSource(), database().dialect(), tables, TestDatabase.TIMEOUT).initSchema();
        String query = "select slot from " + tables.slot() + " where namespace = 'ns' and slot < 1024 and last_owner_key = 'host:8080'";
        String plan = plan(query);
        System.out.println(database() + " plan of the owner branch: " + plan);
        if (database() == TestDatabase.MYSQL || database() == TestDatabase.MARIADB || database() == TestDatabase.POSTGRES) {
            assertTrue(plan.toLowerCase(Locale.ROOT).contains(tables.lastOwnerIndex().toLowerCase(Locale.ROOT)), plan);
        }
    }

    /**
     * The plan as the database explains it: MySQL's and MariaDB's JSON plan, which lists the possible keys; PostgreSQL's plan with
     * sequential scans off, so that it shows an index it can use even on a table this small; H2's and SQL Server's
     * as they come (logged only).
     */
    private String plan(String query) throws SQLException {
        try (Connection connection = database().dataSource().getConnection(); Statement statement = connection.createStatement()) {
            switch (database()) {
                case MYSQL, MARIADB -> {
                    // JSON names the possible keys whatever explain_format the server defaults to.
                    return lines(statement, "explain format=json " + query);
                }
                case POSTGRES -> {
                    boolean autoCommit = connection.getAutoCommit();
                    connection.setAutoCommit(false);
                    try {
                        statement.execute("set local enable_seqscan = off");
                        return lines(statement, "explain " + query);
                    } finally {
                        connection.rollback();
                        connection.setAutoCommit(autoCommit);
                    }
                }
                case H2 -> {
                    return lines(statement, "explain " + query);
                }
                default -> {
                    return "not explained";
                }
            }
        }
    }

    private static String lines(Statement statement, String sql) throws SQLException {
        StringBuilder text = new StringBuilder();
        try (ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                text.append(rs.getString(1)).append('\n');
            }
        }
        return text.toString();
    }
}
