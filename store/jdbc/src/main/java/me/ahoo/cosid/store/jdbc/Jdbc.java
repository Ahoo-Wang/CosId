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

import me.ahoo.cosid.store.StoreTimeoutException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import javax.sql.DataSource;

/**
 * What the range and slot stores share: the data source, the dialect, the tables and the timeout. Each operation
 * borrows one connection and gives it back as it found it.
 */
final class Jdbc {
    /** How often a statement the database chose as a deadlock victim runs again. */
    static final int RETRIES = 5;
    final DataSource dataSource;
    final JdbcDialect dialect;
    final JdbcTables tables;
    final int queryTimeoutSeconds;
    final Duration timeout;

    Jdbc(DataSource dataSource, JdbcDialect dialect, JdbcTables tables, Duration timeout) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.tables = tables;
        this.timeout = timeout;
        this.queryTimeoutSeconds = queryTimeoutSeconds(timeout);
    }

    /**
     * {@code queryTimeout} has whole seconds: a timeout is rounded up, zero means none.
     */
    static int queryTimeoutSeconds(Duration timeout) {
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative: " + timeout);
        }
        long seconds = timeout.toSeconds() + (timeout.getNano() > 0 ? 1 : 0);
        return (int) Math.min(Integer.MAX_VALUE, seconds);
    }

    <T> T run(String operation, SessionWork<T> work) {
        for (int attempt = 1; ; attempt++) {
            Connection connection = connection(operation);
            try (connection) {
                return work.run(new JdbcSession(connection, tables, queryTimeoutSeconds));
            } catch (SQLException e) {
                if (attempt < RETRIES && dialect.isRetryable(e)) {
                    continue;
                }
                if (dialect.isTimeout(e)) {
                    throw new StoreTimeoutException(operation + " timed out on " + dialect.name() + " after " + timeout, e);
                }
                throw new JdbcStoreException(operation + " failed on " + dialect.name(), e);
            }
        }
    }

    /**
     * Borrows a connection. A pool that hands none out in time ({@link SQLTransientConnectionException}, a timeout,
     * or a connection exception of SQL state class {@code 08}) is a store timeout like a statement timeout.
     */
    private Connection connection(String operation) {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            if (isConnectionTimeout(e)) {
                throw new StoreTimeoutException(operation + " got no " + dialect.name() + " connection", e);
            }
            throw new JdbcStoreException(operation + " got no " + dialect.name() + " connection", e);
        }
    }

    boolean isConnectionTimeout(SQLException e) {
        String state = e.getSQLState();
        return e instanceof SQLTransientConnectionException || dialect.isTimeout(e) || state != null && state.startsWith("08");
    }

    /**
     * When an operation of several statements started now has to give up: {@code timeout} from now, or never when
     * the store has no timeout.
     */
    long deadline() {
        return timeout.isZero() ? Long.MAX_VALUE : System.nanoTime() + timeout.toNanos();
    }

    /**
     * Throws {@link StoreTimeoutException} once {@code deadline} has passed: a loop of statements that keeps losing
     * races, or a wait for another client, ends within the store timeout like a single statement does.
     */
    void checkDeadline(long deadline, String operation) {
        if (deadline != Long.MAX_VALUE && System.nanoTime() - deadline > 0) {
            throw new StoreTimeoutException(operation + " did not complete on " + dialect.name() + " within " + timeout);
        }
    }

    /**
     * Runs {@code work}, again when it fails with a duplicate key: two first uses of one key raced, and the loser
     * finds the row on the next try.
     */
    <T> T retryingDuplicates(JdbcSession.SqlWork<T> work) throws SQLException {
        for (int attempt = 1; ; attempt++) {
            try {
                return work.run();
            } catch (SQLException e) {
                if (attempt < RETRIES && dialect.isDuplicateKey(e)) {
                    continue;
                }
                throw e;
            }
        }
    }

    @FunctionalInterface
    interface SessionWork<T> {
        T run(JdbcSession session) throws SQLException;
    }
}
