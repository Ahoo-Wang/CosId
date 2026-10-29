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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.RangeExhaustedException;
import me.ahoo.cosid.RangeSealedException;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.segment.RangeStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * {@code reserve} on MySQL and MariaDB never reads a {@code last_insert_id} an earlier statement left on a pooled
 * connection (06-stores.md §2.1): every branch of the upsert sets it, the insert branch through
 * {@code last_insert_id(? + ?)}. The store here runs on one physical connection, and before each reserve the case
 * leaves a stale value on it, as the previous user of a pooled connection would.
 */
abstract class LastInsertIdTest {
    private static final long STALE = 424_242;
    private Connection physical;
    private RangeStore store;

    abstract TestDatabase database();

    @BeforeEach
    void open() throws SQLException {
        database().assumeAvailable();
        database().dataSource();
        String[] settings = database().connectionSettings();
        physical = DriverManager.getConnection(settings[0], settings[1], settings[2]);
        store = new JdbcStore(new OneConnection(physical), database().dialect(), JdbcTables.DEFAULT, TestDatabase.TIMEOUT).ranges();
    }

    @AfterEach
    void close() throws SQLException {
        if (physical != null) {
            physical.close();
        }
    }

    private void leaveStaleLastInsertId() throws SQLException {
        try (Statement statement = physical.createStatement()) {
            statement.execute("select last_insert_id(" + STALE + ")");
        }
    }

    private static String newKey() {
        return "tck/last-insert-id/" + UUID.randomUUID();
    }

    @Test
    void insertBranchSetsLastInsertId() throws SQLException {
        String key = newKey();
        leaveStaleLastInsertId();
        assertEquals(new Range(7, 17), store.reserve(key, 10, 7), "a new key starts at its offset, not after the stale value");
    }

    @Test
    void updateBranchSetsLastInsertId() throws SQLException {
        String key = newKey();
        store.reserve(key, 10, 0);
        leaveStaleLastInsertId();
        assertEquals(new Range(10, 15), store.reserve(key, 5, 0));
    }

    @Test
    void rejectingBranchSetsLastInsertIdToo() throws SQLException {
        String sealed = newKey();
        store.reserve(sealed, 10, 0);
        assertTrue(store.seal(sealed));
        leaveStaleLastInsertId();
        assertThrows(RangeSealedException.class, () -> store.reserve(sealed, 10, 0), "a sealed key is not reserved at the stale value");

        String full = newKey();
        store.reserve(full, 5, Long.MAX_VALUE - 10);
        leaveStaleLastInsertId();
        assertThrows(RangeExhaustedException.class, () -> store.reserve(full, 10, 0), "a full key is not reserved at the stale value");
    }

    /**
     * R1-jdbc-06: behind an SQL proxy that loses the generated key, or answers with one the statement did not set, a
     * reserve fails instead of handing out a range; {@link FetchPlan#TRANSACTION} needs no generated key at all.
     */
    @Test
    void aLostOrRewrittenGeneratedKeyFailsAndTheTransactionPlanNeedsNone() throws SQLException {
        String key = newKey();
        store.reserve(key, 10, 0);
        RangeStore lost = new JdbcStore(new OneConnection(physical, -1), database().dialect(), JdbcTables.DEFAULT, TestDatabase.TIMEOUT).ranges();
        IllegalStateException noKey = assertThrows(IllegalStateException.class, () -> lost.reserve(key, 10, 0));
        assertTrue(noKey.getMessage().contains("set options.fetchPlan=transaction"), noKey.getMessage());
        RangeStore rewritten = new JdbcStore(new OneConnection(physical, 3), database().dialect(), JdbcTables.DEFAULT, TestDatabase.TIMEOUT).ranges();
        IllegalStateException small = assertThrows(IllegalStateException.class, () -> rewritten.reserve(key, 10, 0));
        assertTrue(small.getMessage().contains("below the size"), small.getMessage());
        // A sealed key still reads as sealed without a generated key.
        String sealed = newKey();
        store.reserve(sealed, 10, 0);
        assertTrue(store.seal(sealed));
        assertThrows(RangeSealedException.class, () -> lost.reserve(sealed, 10, 0));

        RangeStore transactional = new JdbcStore(new OneConnection(physical, -1), database().dialect(), JdbcTables.DEFAULT, TestDatabase.TIMEOUT,
            FetchPlan.TRANSACTION).ranges();
        long before = store.reserve(key, 1, 0).end();
        assertEquals(new Range(before, before + 10), transactional.reserve(key, 10, 0));
        String fresh = newKey();
        assertEquals(new Range(7, 17), transactional.reserve(fresh, 10, 7));
    }

    /** A data source of one physical connection that its users cannot close, as a pool of one hands it out. */
    private static final class OneConnection implements DataSource {
        private final Connection connection;

        private OneConnection(Connection physical) {
            this(physical, 0);
        }

        /**
         * @param generatedKey 0 to pass the generated keys through; -1 for a proxy that loses them; otherwise the key
         *                     a proxy answers instead
         */
        private OneConnection(Connection physical, long generatedKey) {
            this.connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    try {
                        Object result = method.invoke(physical, args);
                        return generatedKey != 0 && result instanceof PreparedStatement statement ? keys(statement, generatedKey) : result;
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        }

        /** A statement whose generated keys are lost (-1) or replaced by another key. */
        private static PreparedStatement keys(PreparedStatement statement, long generatedKey) {
            return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[] {PreparedStatement.class},
                (proxy, method, args) -> {
                    if ("getGeneratedKeys".equals(method.getName())) {
                        String sql = generatedKey < 0 ? "select 1 from dual where 1 = 0" : "select " + generatedKey;
                        return statement.getConnection().createStatement().executeQuery(sql);
                    }
                    try {
                        return method.invoke(statement, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        }

        @Override
        public Connection getConnection() {
            return connection;
        }

        @Override
        public Connection getConnection(String username, String password) {
            return connection;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> type) throws SQLException {
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> type) {
            return false;
        }
    }
}
