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
import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
     * {@link #INCREMENT_MAX_ID_SQL} that also returns the new max id as the statement's generated key
     * ({@code last_insert_id(expr)}): one statement under auto-commit, without a transaction (MySQL, MariaDB).
     *
     * @since 4.1.0
     */
    public static final String LAST_INSERT_ID_INCREMENT_MAX_ID_SQL
        = "update cosid set last_max_id=last_insert_id(last_max_id + ?),last_fetch_time=unix_timestamp() where name = ?";

    private final DataSource dataSource;
    private final String incrementMaxIdSql;
    private final String fetchMaxIdSql;
    private final int queryTimeoutSeconds;
    private final FetchMode fetchMode;
    private final FetchPlan fetchPlan;
    /**
     * The statements and plan for the database, once its product was read.
     */
    private volatile @Nullable Statements confirmed;

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
        this.fetchMode = fetchMode;
        this.fetchPlan = FetchPlan.of(incrementMaxIdSql, fetchMaxIdSql, fetchMode);
    }
    
    /**
     * Whether the built-in statements may take the shorter paths.
     *
     * @since 4.1.0
     */
    public enum FetchMode {
        /**
         * The built-in statements of the database the connection reports run in one round-trip under auto-commit: the
         * MySQL ones as {@code last_insert_id}, those of the other {@link JdbcDialect}s as one query
         * ({@link JdbcDialect#getIncrementAndFetchMaxIdSql()}). The MySQL statements, the defaults, on another
         * {@link JdbcDialect} database are replaced by its own. Any other statements, or any database not of
         * {@link JdbcDialect}, run in a transaction.
         */
        AUTO,
        /**
         * Always the update then the select in one transaction, as before 4.1: for SQL proxies and read/write splitters
         * (ShardingSphere, MyCat, ProxySQL, Vitess) that may not pass the update's generated key through.
         */
        TRANSACTION
    }
    
    /**
     * How a segment is fetched: the built-in statements of a {@link JdbcDialect} take one round-trip under
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
            return JdbcDialect.ofSegmentStatements(incrementMaxIdSql, fetchMaxIdSql)
                .map(dialect -> dialect == JdbcDialect.MYSQL ? LAST_INSERT_ID : dialect.getIncrementAndFetchMaxIdSql() != null ? RETURNING : TRANSACTION)
                .orElse(TRANSACTION);
        }
        
        /**
         * The same statement, whatever its whitespace or final semicolon: as configured in YAML or as a constant.
         */
        static boolean sameSql(String configured, String builtIn) {
            return JdbcDialect.isSameStatement(configured, builtIn);
        }
    }
    
    /**
     * The plan of the statements, taken once the connection's database is that of the statements.
     */
    FetchPlan getFetchPlan() {
        return fetchPlan;
    }
    
    /**
     * What a fetch runs: the plan and its statements.
     */
    record Statements(FetchPlan plan, String incrementMaxIdSql, String fetchMaxIdSql, @Nullable String incrementAndFetchMaxIdSql) {
    }
    
    /**
     * The statements for the connection's database, read from its metadata on the first fetch and kept once read. Until
     * then, or when it cannot be read, the configured statements in a transaction.
     */
    Statements statements(Connection connection) {
        Statements current = confirmed;
        if (current != null) {
            return current;
        }
        String product;
        try {
            product = connection.getMetaData().getDatabaseProductName();
        } catch (SQLException | RuntimeException metadataFailure) {
            if (log.isDebugEnabled()) {
                log.debug("Segment [{}]: can not read the database product, fetching in a transaction.", getNamespacedName(), metadataFailure);
            }
            return new Statements(FetchPlan.TRANSACTION, incrementMaxIdSql, fetchMaxIdSql, null);
        }
        JdbcDialect database = JdbcDialect.ofProduct(connection, product).orElse(null);
        Statements resolved = resolve(database);
        if (log.isInfoEnabled()) {
            log.info("Segment [{}] on [{}]: {} fetches{}.", getNamespacedName(), product, resolved.plan(),
                resolved.incrementMaxIdSql().equals(incrementMaxIdSql) ? "" : " with the statements of " + database);
        }
        confirmed = resolved;
        return resolved;
    }
    
    private Statements resolve(@Nullable JdbcDialect database) {
        JdbcDialect ofStatements = JdbcDialect.ofSegmentStatements(incrementMaxIdSql, fetchMaxIdSql).orElse(null);
        if (database == null || ofStatements == null || (ofStatements != database && ofStatements != JdbcDialect.MYSQL)) {
            return new Statements(FetchPlan.TRANSACTION, incrementMaxIdSql, fetchMaxIdSql, null);
        }
        // The statements of the database, or the MySQL defaults left as they are on another one: the database's own.
        FetchPlan plan;
        if (fetchMode == FetchMode.TRANSACTION) {
            plan = FetchPlan.TRANSACTION;
        } else if (database == JdbcDialect.MYSQL) {
            plan = FetchPlan.LAST_INSERT_ID;
        } else {
            plan = database.getIncrementAndFetchMaxIdSql() != null ? FetchPlan.RETURNING : FetchPlan.TRANSACTION;
        }
        return new Statements(plan, database.getIncrementMaxIdSql(), database.getFetchMaxIdSql(), database.getIncrementAndFetchMaxIdSql());
    }

    @Override
    protected long doNextMaxId(long step) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            try {
                Statements statements = statements(connection);
                return switch (statements.plan()) {
                    case LAST_INSERT_ID -> withAutoCommit(connection, autoCommit, () -> lastInsertId(connection, step));
                    case RETURNING -> withAutoCommit(connection, autoCommit, () -> returning(connection, statements, step));
                    default -> inTransaction(connection, autoCommit, statements, step);
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
    
    private long inTransaction(Connection connection, boolean autoCommit, Statements statements, long step) throws SQLException {
        if (autoCommit) {
            connection.setAutoCommit(false);
        }
        try {
            long nextMaxId = incrementAndFetch(connection, statements, step);
            connection.commit();
            return nextMaxId;
        } catch (Throwable throwable) {
            // Never hand a connection with an open transaction (and its row lock) back to the pool.
            JdbcSupport.rollbackQuietly(connection, throwable);
            throw throwable;
        }
    }
    
    private long lastInsertId(Connection connection, long step) throws SQLException {
        // The generated key is this statement's own last_insert_id(expr), from its OK packet: unlike a later
        // "select last_insert_id()", nothing else on the connection or behind a proxy can change it.
        try (PreparedStatement accStatement = JdbcSupport.prepareStatement(connection, LAST_INSERT_ID_INCREMENT_MAX_ID_SQL, queryTimeoutSeconds,
            Statement.RETURN_GENERATED_KEYS)) {
            accStatement.setLong(1, step);
            accStatement.setString(2, getNamespacedName());
            if (accStatement.executeUpdate() == 0) {
                throw new SegmentNameMissingException(getNamespacedName());
            }
            try (ResultSet generatedKeys = accStatement.getGeneratedKeys()) {
                if (!generatedKeys.next()) {
                    throw new CosIdException(Checks.lenientFormat("Segment [%s]: the update returned no generated key (a SQL proxy or driver that "
                        + "drops it?). Set cosid.segment.distributor.jdbc.fetch-plan=transaction.", getNamespacedName()));
                }
                long maxId = generatedKeys.getLong(1);
                // The update set last_max_id >= step (the offset is never negative): less means the key is not this
                // update's value, and handing (maxId - step, maxId] out would duplicate ids.
                if (maxId < step) {
                    throw new CosIdException(Checks.lenientFormat("Segment [%s]: the update returned [%s] after adding [%s], not its new max id "
                        + "(a SQL proxy or read/write splitter?). Set cosid.segment.distributor.jdbc.fetch-plan=transaction.",
                        getNamespacedName(), maxId, step));
                }
                return maxId;
            }
        }
    }
    
    private long returning(Connection connection, Statements statements, long step) throws SQLException {
        String incrementAndFetchMaxIdSql = Checks.checkNotNull(statements.incrementAndFetchMaxIdSql(), "incrementAndFetchMaxIdSql");
        try (PreparedStatement statement = JdbcSupport.prepareStatement(connection, incrementAndFetchMaxIdSql, queryTimeoutSeconds)) {
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
    
    private long incrementAndFetch(Connection connection, Statements statements, long step) throws SQLException {
        try (PreparedStatement accStatement = JdbcSupport.prepareStatement(connection, statements.incrementMaxIdSql(), queryTimeoutSeconds)) {
            accStatement.setLong(1, step);
            accStatement.setString(2, getNamespacedName());
            int affected = accStatement.executeUpdate();
            if (affected == 0) {
                throw new SegmentNameMissingException(getNamespacedName());
            }
        }

        try (PreparedStatement fetchStatement = JdbcSupport.prepareStatement(connection, statements.fetchMaxIdSql(), queryTimeoutSeconds)) {
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
