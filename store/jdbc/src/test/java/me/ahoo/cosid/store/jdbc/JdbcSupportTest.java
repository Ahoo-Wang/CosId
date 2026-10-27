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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The plumbing every operation shares: timeouts, transactions, retries, table names.
 */
class JdbcSupportTest {
    private static DriverManagerDataSource h2() {
        return new DriverManagerDataSource("jdbc:h2:mem:support-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    }

    @Test
    void timeoutBecomesWholeQueryTimeoutSeconds() {
        assertEquals(0, Jdbc.queryTimeoutSeconds(Duration.ZERO), "zero is no timeout");
        assertEquals(1, Jdbc.queryTimeoutSeconds(Duration.ofMillis(1)));
        assertEquals(2, Jdbc.queryTimeoutSeconds(Duration.ofMillis(1500)));
        assertEquals(3, Jdbc.queryTimeoutSeconds(Duration.ofSeconds(3)));
        assertEquals(Integer.MAX_VALUE, Jdbc.queryTimeoutSeconds(Duration.ofDays(100_000)));
        assertThrows(IllegalArgumentException.class, () -> Jdbc.queryTimeoutSeconds(Duration.ofSeconds(-1)));
    }

    @Test
    void statementsCarryTheTimeout() {
        Jdbc jdbc = new Jdbc(h2(), JdbcDialects.H2, JdbcTables.DEFAULT, Duration.ofMillis(2500));
        int timeout = jdbc.run("probe", session -> {
            try (PreparedStatement statement = session.prepare("select 1")) {
                return statement.getQueryTimeout();
            }
        });
        assertEquals(3, timeout);
    }

    @Test
    void transactionRollsBackAndRestoresTheConnection() throws SQLException {
        DriverManagerDataSource dataSource = h2();
        Jdbc jdbc = new Jdbc(dataSource, JdbcDialects.H2, JdbcTables.DEFAULT, Duration.ofSeconds(5));
        jdbc.run("schema", session -> session.connection().createStatement().execute("create table t (v int)"));
        try (Connection connection = dataSource.getConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            JdbcSession session = new JdbcSession(connection, JdbcTables.DEFAULT, 5, java.util.Optional.empty());
            IllegalStateException failure = new IllegalStateException("boom");
            IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> session.transaction(() -> {
                assertEquals(Connection.TRANSACTION_READ_COMMITTED, connection.getTransactionIsolation());
                connection.createStatement().execute("insert into t values (1)");
                throw failure;
            }));
            assertSame(failure, thrown);
            assertTrue(connection.getAutoCommit());
            assertEquals(Connection.TRANSACTION_SERIALIZABLE, connection.getTransactionIsolation());
            try (var rs = connection.createStatement().executeQuery("select count(*) from t")) {
                rs.next();
                assertEquals(0, rs.getInt(1), "rolled back");
            }
            assertEquals(7, session.transaction(() -> 7));
        }
    }

    @Test
    void deadlockVictimsRunAgainOtherFailuresDoNot() {
        Jdbc jdbc = new Jdbc(h2(), JdbcDialects.H2, JdbcTables.DEFAULT, Duration.ofSeconds(5));
        AtomicInteger calls = new AtomicInteger();
        assertEquals("ok", jdbc.run("deadlocking", session -> {
            if (calls.incrementAndGet() < 3) {
                throw new SQLException("deadlock", "40001");
            }
            return "ok";
        }));
        assertEquals(3, calls.get());

        calls.set(0);
        JdbcStoreException always = assertThrows(JdbcStoreException.class, () -> jdbc.run("always", session -> {
            calls.incrementAndGet();
            throw new SQLException("deadlock", "40001");
        }));
        assertEquals(Jdbc.RETRIES, calls.get());
        assertTrue(always.getMessage().startsWith("always failed on h2: deadlock"), always.getMessage());

        calls.set(0);
        assertThrows(JdbcStoreException.class, () -> jdbc.run("broken", session -> {
            calls.incrementAndGet();
            throw new SQLException("syntax", "42000");
        }));
        assertEquals(1, calls.get());
    }

    @Test
    void duplicatesRunAgainABoundedNumberOfTimes() throws SQLException {
        Jdbc jdbc = new Jdbc(h2(), JdbcDialects.H2, JdbcTables.DEFAULT, Duration.ofSeconds(5));
        AtomicInteger calls = new AtomicInteger();
        assertEquals(2, jdbc.retryingDuplicates(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new SQLException("dup", "23505");
            }
            return calls.get();
        }));
        calls.set(0);
        assertThrows(SQLException.class, () -> jdbc.retryingDuplicates(() -> {
            calls.incrementAndGet();
            throw new SQLException("dup", "23505");
        }));
        assertEquals(Jdbc.RETRIES, calls.get());
    }

    @Test
    void tableNamesAreIdentifiers() {
        assertEquals(JdbcTables.DEFAULT, JdbcTables.of(Map.of()));
        assertEquals(new JdbcTables("app.ids", "cosid_slot", "cosid_namespace"), JdbcTables.of(Map.of("rangeTable", "app.ids")));
        for (String bad : new String[] {"", "1abc", "a b", "a;b", "a.b.c", "a-b", "\"quoted\""}) {
            assertThrows(IllegalArgumentException.class, () -> new JdbcTables(bad, "s", "n"), bad);
        }
        assertThrows(NullPointerException.class, () -> new JdbcTables("r", null, "n"));
        assertEquals("select * from r join s on n.x = cosid_rangex",
            new JdbcTables("r", "s", "n").rename("select * from cosid_range join cosid_slot on cosid_namespace.x = cosid_rangex"));
    }
}
