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

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.internal.Checks;
import me.ahoo.cosid.jdbc.exception.NotFoundMaxIdException;
import me.ahoo.cosid.jdbc.exception.SegmentNameMissingException;
import me.ahoo.cosid.segment.AbstractIdSegmentDistributor;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import javax.sql.DataSource;

/**
 * Jdbc IdSegment Distributor.
 *
 * @author ahoo wang
 */
@Slf4j
public class JdbcIdSegmentDistributor extends AbstractIdSegmentDistributor {

    public static final String INCREMENT_MAX_ID_SQL
        = "update cosid set last_max_id=(last_max_id + ?),last_fetch_time=unix_timestamp() where name = ?;";
    public static final String FETCH_MAX_ID_SQL
        = "select last_max_id from cosid where name = ?;";
    /**
     * {@link #INCREMENT_MAX_ID_SQL} that also keeps the new max id as the connection's {@code last_insert_id()}: with
     * {@link #LAST_INSERT_ID_SQL}, two statements under auto-commit, without a transaction (MySQL, MariaDB).
     *
     * @since 4.1.0
     */
    public static final String LAST_INSERT_ID_INCREMENT_MAX_ID_SQL
        = "update cosid set last_max_id=last_insert_id(last_max_id + ?),last_fetch_time=unix_timestamp() where name = ?";
    /**
     * Reads the max id {@link #LAST_INSERT_ID_INCREMENT_MAX_ID_SQL} kept on the same connection.
     *
     * @since 4.1.0
     */
    public static final String LAST_INSERT_ID_SQL = "select last_insert_id()";

    private final DataSource dataSource;
    private final String incrementMaxIdSql;
    private final String fetchMaxIdSql;
    private final int queryTimeoutSeconds;
    private final FetchPlan fetchPlan;

    public JdbcIdSegmentDistributor(String namespace, String name, long step, DataSource dataSource) {
        this(namespace, name, step, INCREMENT_MAX_ID_SQL, FETCH_MAX_ID_SQL, dataSource);
    }

    public JdbcIdSegmentDistributor(String namespace, String name, long step, String incrementMaxIdSql, String fetchMaxIdSql, DataSource dataSource) {
        this(namespace, name, step, incrementMaxIdSql, fetchMaxIdSql, dataSource, JdbcSupport.DEFAULT_QUERY_TIMEOUT);
    }

    /**
     * Create a JDBC segment distributor.
     *
     * @param queryTimeout timeout applied to each statement, {@code null} or zero means no timeout
     */
    public JdbcIdSegmentDistributor(String namespace, String name, long step, String incrementMaxIdSql, String fetchMaxIdSql, DataSource dataSource,
                                    Duration queryTimeout) {
        this(namespace, name, step, incrementMaxIdSql, fetchMaxIdSql, dataSource, queryTimeout, FetchMode.AUTO);
    }
    
    /**
     * Create a JDBC segment distributor.
     *
     * @param queryTimeout timeout applied to each statement, {@code null} or zero means no timeout
     * @param fetchMode    {@link FetchMode#TRANSACTION} to always fetch in a transaction
     * @since 4.1.0
     */
    public JdbcIdSegmentDistributor(String namespace, String name, long step, String incrementMaxIdSql, String fetchMaxIdSql, DataSource dataSource,
                                    Duration queryTimeout, FetchMode fetchMode) {
        super(namespace, name, step);
        Checks.checkArgument(!Checks.isNullOrEmpty(incrementMaxIdSql), "incrementMaxIdSql can not be empty!");
        Checks.checkArgument(!Checks.isNullOrEmpty(fetchMaxIdSql), "fetchMaxIdSql can not be empty!");
        Checks.checkNotNull(dataSource, "dataSource can not be null!");

        this.incrementMaxIdSql = incrementMaxIdSql;
        this.fetchMaxIdSql = fetchMaxIdSql;
        this.dataSource = dataSource;
        this.queryTimeoutSeconds = JdbcSupport.toQueryTimeoutSeconds(queryTimeout);
        this.fetchPlan = FetchPlan.of(incrementMaxIdSql, fetchMaxIdSql, fetchMode);
    }
    
    /**
     * Whether the built-in statements may take the shorter paths.
     *
     * @since 4.1.0
     */
    public enum FetchMode {
        /**
         * The built-in MySQL statements run as {@code last_insert_id} under auto-commit, the PostgreSQL ones as one
         * {@code update ... returning}; other statements in a transaction.
         */
        AUTO,
        /**
         * Always the update then the select in one transaction, as before 4.1: for SQL proxies and read/write splitters
         * (ShardingSphere, MyCat, ProxySQL, Vitess) that may send the {@code select last_insert_id()} to another session.
         */
        TRANSACTION
    }
    
    /**
     * How a segment is fetched: the built-in MySQL and PostgreSQL statements take one or two round-trips under
     * auto-commit; any other statements run in a transaction, the update then the select.
     */
    enum FetchPlan {
        TRANSACTION,
        LAST_INSERT_ID,
        RETURNING;
        
        static FetchPlan of(String incrementMaxIdSql, String fetchMaxIdSql, FetchMode fetchMode) {
            return fetchMode == FetchMode.TRANSACTION ? TRANSACTION : of(incrementMaxIdSql, fetchMaxIdSql);
        }
        
        static FetchPlan of(String incrementMaxIdSql, String fetchMaxIdSql) {
            if (sameSql(incrementMaxIdSql, INCREMENT_MAX_ID_SQL) && sameSql(fetchMaxIdSql, FETCH_MAX_ID_SQL)) {
                return LAST_INSERT_ID;
            }
            if (sameSql(incrementMaxIdSql, PostgreSqlDialect.INCREMENT_MAX_ID_SQL) && sameSql(fetchMaxIdSql, PostgreSqlDialect.FETCH_MAX_ID_SQL)) {
                return RETURNING;
            }
            return TRANSACTION;
        }
        
        /**
         * The same statement, whatever its whitespace or final semicolon: as configured in YAML or as a constant.
         */
        static boolean sameSql(String configured, String builtIn) {
            return normalize(configured).equals(normalize(builtIn));
        }
        
        private static String normalize(String sql) {
            // Case matters: "update COSID ..." names another table on a case-sensitive MySQL.
            String normalized = sql.trim().replaceAll("\\s+", " ");
            while (normalized.endsWith(";")) {
                normalized = normalized.substring(0, normalized.length() - 1).trim();
            }
            return normalized.replace(" ,", ",").replace(", ", ",").replace(" =", "=").replace("= ", "=");
        }
    }
    
    FetchPlan getFetchPlan() {
        return fetchPlan;
    }




    @Override
    protected long doNextMaxId(long step) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            try {
                return switch (fetchPlan) {
                    case LAST_INSERT_ID -> withAutoCommit(connection, autoCommit, () -> lastInsertId(connection, step));
                    case RETURNING -> withAutoCommit(connection, autoCommit, () -> returning(connection, step));
                    default -> inTransaction(connection, autoCommit, step);
                };
            } finally {
                // A pooled connection goes back with the auto-commit mode it was handed out with.
                restoreAutoCommit(connection, autoCommit);
            }
        } catch (SQLException sqlException) {
            if (log.isErrorEnabled()) {
                log.error(sqlException.getMessage(), sqlException);
            }
            throw new CosIdException(sqlException.getMessage(), sqlException);
        }
    }
    
    @FunctionalInterface
    private interface SqlCall {
        long call() throws SQLException;
    }
    
    private static long withAutoCommit(Connection connection, boolean autoCommit, SqlCall call) throws SQLException {
        if (!autoCommit) {
            connection.setAutoCommit(true);
        }
        return call.call();
    }
    
    private static void restoreAutoCommit(Connection connection, boolean autoCommit) {
        try {
            if (connection.getAutoCommit() != autoCommit) {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException restoreException) {
            // Never hide the fetch's own outcome: the pool validates or discards a connection it cannot reset.
            if (log.isWarnEnabled()) {
                log.warn("Restoring auto-commit [{}] failed.", autoCommit, restoreException);
            }
        }
    }
    
    private long inTransaction(Connection connection, boolean autoCommit, long step) throws SQLException {
        if (autoCommit) {
            connection.setAutoCommit(false);
        }
        try {
            long nextMaxId = incrementAndFetch(connection, step);
            connection.commit();
            return nextMaxId;
        } catch (Throwable throwable) {
            // Never hand a connection with an open transaction (and its row lock) back to the pool.
            JdbcSupport.rollbackQuietly(connection, throwable);
            throw throwable;
        }
    }
    
    private long lastInsertId(Connection connection, long step) throws SQLException {
        try (PreparedStatement accStatement = JdbcSupport.prepareStatement(connection, LAST_INSERT_ID_INCREMENT_MAX_ID_SQL, queryTimeoutSeconds)) {
            accStatement.setLong(1, step);
            accStatement.setString(2, getNamespacedName());
            if (accStatement.executeUpdate() == 0) {
                throw new SegmentNameMissingException(getNamespacedName());
            }
        }
        try (PreparedStatement fetchStatement = JdbcSupport.prepareStatement(connection, LAST_INSERT_ID_SQL, queryTimeoutSeconds);
             ResultSet resultSet = fetchStatement.executeQuery()) {
            if (!resultSet.next()) {
                throw new NotFoundMaxIdException(getNamespacedName());
            }
            long maxId = resultSet.getLong(1);
            // The update set last_max_id >= step (the offset is never negative). Less, typically 0, means the select ran
            // on another session than the update: a proxy or read/write splitter. Handing (maxId - step, maxId] out would
            // duplicate ids.
            if (maxId < step) {
                throw new CosIdException(Checks.lenientFormat("Segment [%s]: last_insert_id() returned [%s] after adding [%s]: the select did not run "
                    + "on the update's session (a SQL proxy or read/write splitter?). Set cosid.segment.distributor.jdbc.fetch-plan=transaction.",
                    getNamespacedName(), maxId, step));
            }
            return maxId;
        }
    }
    
    private long returning(Connection connection, long step) throws SQLException {
        try (PreparedStatement statement = JdbcSupport.prepareStatement(connection, PostgreSqlDialect.INCREMENT_AND_FETCH_MAX_ID_SQL, queryTimeoutSeconds)) {
            statement.setLong(1, step);
            statement.setString(2, getNamespacedName());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SegmentNameMissingException(getNamespacedName());
                }
                return resultSet.getLong(1);
            }
        }
    }
    
    private long incrementAndFetch(Connection connection, long step) throws SQLException {
        try (PreparedStatement accStatement = JdbcSupport.prepareStatement(connection, incrementMaxIdSql, queryTimeoutSeconds)) {
            accStatement.setLong(1, step);
            accStatement.setString(2, getNamespacedName());
            int affected = accStatement.executeUpdate();
            if (affected == 0) {
                throw new SegmentNameMissingException(getNamespacedName());
            }
        }

        try (PreparedStatement fetchStatement = JdbcSupport.prepareStatement(connection, fetchMaxIdSql, queryTimeoutSeconds)) {
            fetchStatement.setString(1, getNamespacedName());
            try (ResultSet resultSet = fetchStatement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new NotFoundMaxIdException(getNamespacedName());
                }
                return resultSet.getLong(1);
            }
        }
    }

}
