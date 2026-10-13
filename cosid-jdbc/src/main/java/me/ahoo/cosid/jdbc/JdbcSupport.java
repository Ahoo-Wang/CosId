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

package me.ahoo.cosid.jdbc;

import me.ahoo.cosid.internal.Checks;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Duration;
import java.util.Set;

/**
 * Shared JDBC helpers: statement query timeout, transaction rollback and duplicate key detection.
 *
 * @author ahoo wang
 */
@Slf4j
public final class JdbcSupport {
    /**
     * Default query timeout applied to every distributor statement.
     */
    public static final Duration DEFAULT_QUERY_TIMEOUT = Duration.ofSeconds(5);
    /**
     * SQLState class {@code 23}: integrity constraint violation (SQL standard).
     */
    private static final String INTEGRITY_CONSTRAINT_VIOLATION_CLASS = "23";
    /**
     * SQLState {@code 23505}: unique violation.
     */
    private static final String UNIQUE_VIOLATION = "23505";
    /**
     * Duplicate-key vendor error codes: MySQL/MariaDB 1062, Oracle ORA-00001, SQL Server 2627 and 2601.
     */
    private static final Set<Integer> DUPLICATE_KEY_VENDOR_CODES = Set.of(1062, 1, 2627, 2601);
    private static final Set<String> ALREADY_EXISTS_STATES = Set.of("42S01", "42S11", "42P07", "X0Y32");
    private static final Set<Integer> ALREADY_EXISTS_VENDOR_CODES = Set.of(1050, 1061, 2714, 1913, 955, 42101, 42111);

    private JdbcSupport() {
    }

    /**
     * Converts a query timeout to the whole seconds expected by {@link java.sql.Statement#setQueryTimeout(int)}.
     * Sub-second values are rounded up so that a positive timeout never turns into "no timeout".
     *
     * @param queryTimeout query timeout, {@code null} or zero means no timeout
     * @return timeout in seconds, {@code 0} means no timeout
     */
    public static int toQueryTimeoutSeconds(Duration queryTimeout) {
        if (queryTimeout == null || queryTimeout.isZero()) {
            return 0;
        }
        Checks.checkArgument(!queryTimeout.isNegative(), "queryTimeout:[%s] can not be negative!", queryTimeout);
        long millis = queryTimeout.toMillis();
        long seconds = (millis + 999) / 1000;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, seconds));
    }

    static PreparedStatement prepareStatement(Connection connection, String sql, int queryTimeoutSeconds) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        if (queryTimeoutSeconds > 0) {
            try {
                statement.setQueryTimeout(queryTimeoutSeconds);
            } catch (SQLException e) {
                statement.close();
                throw e;
            }
        }
        return statement;
    }

    /**
     * Whether the exception (or any exception in its {@link SQLException#getNextException() chain}) reports a duplicate key.
     *
     * <p>SQLState class {@code 23} alone is too broad: it also covers NOT NULL, foreign key and CHECK violations, and
     * MySQL, Oracle and SQL Server report every integrity violation as {@code 23000}. So this matches
     * SQLState {@code 23505} (PostgreSQL, H2, Derby, DB2, HSQLDB) or a class {@code 23} error with a known duplicate-key
     * vendor code: MySQL/MariaDB {@code 1062}, Oracle {@code 1} (ORA-00001), SQL Server {@code 2627}/{@code 2601}.
     * A {@link SQLIntegrityConstraintViolationException} without any SQLState is also treated as a duplicate.</p>
     *
     * @param sqlException the exception thrown by the driver
     * @return true if it is a duplicate key violation
     */
    public static boolean isDuplicateKey(SQLException sqlException) {
        for (SQLException current = sqlException; current != null; current = current.getNextException()) {
            String sqlState = current.getSQLState();
            if (UNIQUE_VIOLATION.equals(sqlState)) {
                return true;
            }
            if (sqlState == null) {
                if (current instanceof SQLIntegrityConstraintViolationException) {
                    return true;
                }
                continue;
            }
            if (sqlState.startsWith(INTEGRITY_CONSTRAINT_VIOLATION_CLASS) && DUPLICATE_KEY_VENDOR_CODES.contains(current.getErrorCode())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the exception (or one in its chain) reports that a table or an index already exists: what DDL without
     * {@code if not exists} reports on the second start. SQLStates {@code 42S01}/{@code 42S11} (MySQL, MariaDB, H2),
     * {@code 42P07} (PostgreSQL), {@code X0Y32} (Derby); vendor codes MySQL {@code 1050}/{@code 1061}, SQL Server
     * {@code 2714}/{@code 1913}, Oracle {@code 955}, H2 {@code 42101}/{@code 42111}.
     *
     * @param sqlException the exception thrown by the driver
     * @return true if the object being created already exists
     * @since 4.1.0
     */
    public static boolean isAlreadyExists(SQLException sqlException) {
        for (SQLException current = sqlException; current != null; current = current.getNextException()) {
            if (ALREADY_EXISTS_STATES.contains(current.getSQLState()) || ALREADY_EXISTS_VENDOR_CODES.contains(current.getErrorCode())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Commit when the connection is not in auto-commit mode, e.g. from a pool configured with {@code autoCommit=false};
     * otherwise the writes would be rolled back when the connection is closed or returned.
     */
    static void commitIfManual(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.commit();
        }
    }
    
    /**
     * Roll back after a failure when the connection is not in auto-commit mode.
     */
    static void rollbackIfManual(Connection connection, Throwable cause) {
        try {
            if (!connection.getAutoCommit()) {
                rollbackQuietly(connection, cause);
            }
        } catch (SQLException autoCommitException) {
            cause.addSuppressed(autoCommitException);
        }
    }
    
    static void rollbackQuietly(Connection connection, Throwable cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            if (cause != null) {
                cause.addSuppressed(rollbackException);
            }
            if (log.isWarnEnabled()) {
                log.warn("Rollback failed: {}", rollbackException.getMessage(), rollbackException);
            }
        }
    }
}
