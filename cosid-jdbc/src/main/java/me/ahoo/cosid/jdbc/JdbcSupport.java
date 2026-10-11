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

import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Duration;

/**
 * Shared JDBC helpers: statement query timeout, transaction rollback and constraint violation detection.
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
        Preconditions.checkArgument(!queryTimeout.isNegative(), "queryTimeout:[%s] can not be negative!", queryTimeout);
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
     * Whether the exception (or any exception in its chain) is an integrity constraint violation,
     * e.g. a duplicate primary key.
     *
     * <p>Not every driver throws {@link SQLIntegrityConstraintViolationException} (PostgreSQL, for one, throws a plain
     * {@link SQLException} with SQLState {@code 23505}), so SQLState class {@code 23} is checked as well.</p>
     *
     * @param sqlException the exception thrown by the driver
     * @return true if it is an integrity constraint violation
     */
    public static boolean isIntegrityConstraintViolation(SQLException sqlException) {
        for (SQLException current = sqlException; current != null; current = current.getNextException()) {
            if (current instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
            String sqlState = current.getSQLState();
            if (sqlState != null && sqlState.startsWith(INTEGRITY_CONSTRAINT_VIOLATION_CLASS)) {
                return true;
            }
        }
        return false;
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
