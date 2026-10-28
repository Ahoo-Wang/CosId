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
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
     * {@code queryTimeout} has whole seconds: a timeout is rounded up. The timeout is positive, as {@code StoreConfig}
     * requires (R1-spi-04).
     */
    static int queryTimeoutSeconds(Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive: " + timeout);
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
     * When an operation of several statements started now has to give up: {@code timeout} from now.
     */
    long deadline() {
        return System.nanoTime() + timeout.toNanos();
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

    /**
     * The columns of a table as {@link DatabaseMetaData#getColumns} shows them, lower case; empty when it shows no such
     * table. A schema-qualified name ({@code s.t}) matches the table of that schema (or catalog: MySQL's databases);
     * an unqualified one the table of the current schema, or of any schema of the current catalog when the current
     * schema has none (a PostgreSQL {@code search_path} beyond its first schema). Names compare ignoring case: the
     * databases fold unquoted identifiers to upper (H2) or lower case (PostgreSQL).
     */
    static Set<String> columns(Connection connection, String table) throws SQLException {
        int dot = table.indexOf('.');
        String qualifier = dot < 0 ? null : table.substring(0, dot);
        String schema = qualifier == null ? connection.getSchema() : null;
        String name = table.substring(dot + 1);
        DatabaseMetaData metaData = connection.getMetaData();
        String pattern = metaData.storesUpperCaseIdentifiers() ? name.toUpperCase(Locale.ROOT)
            : metaData.storesLowerCaseIdentifiers() ? name.toLowerCase(Locale.ROOT) : name;
        Set<String> inSchema = new HashSet<>();
        Set<String> elsewhere = new HashSet<>();
        try (ResultSet rs = metaData.getColumns(qualifier == null ? connection.getCatalog() : null, null, pattern, null)) {
            while (rs.next()) {
                // The name is a pattern: "_" matches any character.
                if (!name.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                    continue;
                }
                String column = rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);
                String tableSchema = rs.getString("TABLE_SCHEM");
                if (qualifier != null) {
                    if (qualifier.equalsIgnoreCase(tableSchema) || qualifier.equalsIgnoreCase(rs.getString("TABLE_CAT"))) {
                        inSchema.add(column);
                    }
                } else if (schema != null && schema.equalsIgnoreCase(tableSchema)) {
                    inSchema.add(column);
                } else {
                    elsewhere.add(column);
                }
            }
        }
        return qualifier != null || !inSchema.isEmpty() ? inSchema : elsewhere;
    }

    /**
     * Whether a table has an index whose leading columns are these, in this order, as
     * {@link DatabaseMetaData#getIndexInfo} shows it; the table is found as {@link #columns} finds it.
     */
    static boolean hasIndex(Connection connection, String table, List<String> leading) throws SQLException {
        int dot = table.indexOf('.');
        String qualifier = dot < 0 ? null : table.substring(0, dot);
        String schema = qualifier == null ? connection.getSchema() : null;
        String name = table.substring(dot + 1);
        DatabaseMetaData metaData = connection.getMetaData();
        String stored = metaData.storesUpperCaseIdentifiers() ? name.toUpperCase(Locale.ROOT)
            : metaData.storesLowerCaseIdentifiers() ? name.toLowerCase(Locale.ROOT) : name;
        Map<String, Map<Integer, String>> inSchema = new HashMap<>();
        Map<String, Map<Integer, String>> elsewhere = new HashMap<>();
        try (ResultSet rs = metaData.getIndexInfo(qualifier == null ? connection.getCatalog() : null, null, stored, false, true)) {
            while (rs.next()) {
                String column = rs.getString("COLUMN_NAME");
                if (column == null || !name.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                    continue;
                }
                String tableSchema = rs.getString("TABLE_SCHEM");
                String index = tableSchema + "." + rs.getString("INDEX_NAME");
                Map<String, Map<Integer, String>> into;
                if (qualifier != null) {
                    if (!qualifier.equalsIgnoreCase(tableSchema) && !qualifier.equalsIgnoreCase(rs.getString("TABLE_CAT"))) {
                        continue;
                    }
                    into = inSchema;
                } else {
                    into = schema != null && schema.equalsIgnoreCase(tableSchema) ? inSchema : elsewhere;
                }
                into.computeIfAbsent(index, k -> new TreeMap<>()).put((int) rs.getShort("ORDINAL_POSITION"), column.toLowerCase(Locale.ROOT));
            }
        }
        for (Map<Integer, String> columns : (qualifier != null || !inSchema.isEmpty() ? inSchema : elsewhere).values()) {
            List<String> ordered = new ArrayList<>(columns.values());
            if (ordered.size() >= leading.size() && ordered.subList(0, leading.size()).equals(leading)) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    interface SessionWork<T> {
        T run(JdbcSession session) throws SQLException;
    }
}
