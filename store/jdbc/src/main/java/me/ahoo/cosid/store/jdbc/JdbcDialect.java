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

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.List;
import java.util.OptionalLong;

/**
 * The SQL of one database product (06-stores.md §2.1), replacing the custom SQL settings of 4.x. The built-in
 * dialects are {@link JdbcDialects}; another one is registered with {@link java.util.ServiceLoader} under this
 * interface, is detected before the built-in ones, and can be named by {@code options.dialect}.
 *
 * <p>The store runs everything else itself: the slot choice of 05 §2.1 as one read of the candidates
 * ({@link #firstRows}) and compare-and-set updates, renewals and releases as compare-and-set updates, all stamped by
 * {@link #nowMillis()}. The
 * tables are those of {@link #schema}: {@code cosid_range(range_key, next_value, sealed)},
 * {@code cosid_slot(namespace, slot, owner_key, incarnation, last_owner_key, fence, expires_at, floor_ms, ceiling_ms,
 * next_floor_ms)} and {@code cosid_namespace(namespace, slot_limit, tolerance_ms, slot_offset)}, under the names of
 * {@link JdbcSession#tables()}. Tables of 5.0.0-alpha.1 have no {@code cosid_namespace.slot_offset}: a missing value
 * reads as 0, and {@code options.initSchema} adds the column ({@link #addColumn}).
 */
public interface JdbcDialect {
    /**
     * The dialect name, for {@code options.dialect} and messages.
     *
     * @return the name, such as {@code mysql}
     */
    String name();

    /**
     * Whether this dialect is the one of a database.
     *
     * @param metaData the metadata of a connection to it
     * @return true if this dialect handles the database
     * @throws SQLException if the metadata cannot be read
     */
    boolean supports(DatabaseMetaData metaData) throws SQLException;

    /**
     * The statements that create the tables when they do not exist ({@code options.initSchema}); each is idempotent.
     *
     * @param tables the table names
     * @return the statements, in order
     */
    List<String> schema(JdbcTables tables);

    /**
     * The store clock as an SQL expression: UTC epoch milliseconds as a {@code bigint}, unaffected by the session
     * time zone and daylight saving time.
     *
     * @return the expression
     */
    String nowMillis();

    /**
     * Reserves {@code size} values of {@code key} in one round trip where the database allows it: a key that does
     * not exist is created with {@code next_value = initialOffset + size}; an existing key that is not sealed and has
     * {@code next_value <= Long.MAX_VALUE - size} gets {@code next_value + size}. The caller guarantees
     * {@code initialOffset <= Long.MAX_VALUE - size}.
     *
     * @param session       the session
     * @param key           the range key
     * @param size          the size, positive
     * @param initialOffset where a new key starts
     * @return the new {@code next_value}; empty if the key is sealed or the range would pass {@code Long.MAX_VALUE}
     * @throws SQLException if the database fails; a duplicate key of two concurrent first reserves is retried
     */
    OptionalLong reserve(JdbcSession session, String key, long size, long initialOffset) throws SQLException;

    /**
     * Selects the first {@code rows} rows of {@code orderBy} that match {@code where}, without locking them
     * ({@code limit}; SQL Server {@code top}). The statement must be usable as a derived table.
     *
     * @param columns the selected columns
     * @param table   the table
     * @param where   the condition
     * @param orderBy the order
     * @param rows    how many rows at most
     * @return the select statement
     */
    default String firstRows(String columns, String table, String where, String orderBy, int rows) {
        return "select " + columns + " from " + table + " where " + where + " order by " + orderBy + " limit " + rows;
    }

    /**
     * The statement that creates an index, for {@code options.initSchema} on a table that lacks it
     * ({@code cosid_slot}'s index on {@code (namespace, last_owner_key)} came after 5.0.0-alpha.1). The store runs it
     * only when the index is missing.
     *
     * @param table   the table, as {@link JdbcTables} names it
     * @param name    the index name, unqualified
     * @param columns the indexed columns, in order
     * @return the statement
     */
    default String createIndex(String table, String name, List<String> columns) {
        return "create index " + name + " on " + table + " (" + String.join(", ", columns) + ")";
    }

    /**
     * The statement that adds a column to an existing table, for {@code options.initSchema} on tables created by an
     * earlier 5.0 pre-release ({@code cosid_namespace.slot_offset} came after 5.0.0-alpha.1). The store runs it only
     * when the column is missing.
     *
     * @param table      the table, as {@link JdbcTables} names it
     * @param column     the column
     * @param definition its type, nullability and default, such as {@code integer not null default 0}
     * @return the statement: {@code alter table <table> add column <column> <definition>} by default
     */
    default String addColumn(String table, String column, String definition) {
        return "alter table " + table + " add column " + column + " " + definition;
    }

    /**
     * Records the parameters of a namespace on its first use; a namespace that exists is left as it is. A slot offset
     * of 0 is the column default: the statement then leaves {@code slot_offset} out, so that it also runs on a table of
     * 5.0.0-alpha.1 that has no such column.
     *
     * @param session         the session
     * @param namespace       the namespace
     * @param limit           the slot limit
     * @param toleranceMillis the tolerance
     * @param slotOffset      the slot offset, not negative
     * @return whether this call inserted the row: the one first use that creates the slot rows
     * @throws SQLException if the database fails; a duplicate key counts as "not inserted"
     */
    default boolean insertNamespaceIfAbsent(JdbcSession session, String namespace, int limit, long toleranceMillis, int slotOffset) throws SQLException {
        try (PreparedStatement insert = session.prepare("insert into " + session.tables().namespace() + " " + JdbcDialects.namespaceInsert(slotOffset))) {
            JdbcDialects.bindNamespace(insert, namespace, limit, toleranceMillis, slotOffset);
            return insert.executeUpdate() > 0;
        }
    }

    /**
     * Creates the rows of slots {@code [0, limit)} of a namespace that do not exist yet, with the column defaults;
     * existing rows are left as they are.
     *
     * @param session   the session
     * @param namespace the namespace
     * @param limit     the slot limit
     * @throws SQLException if the database fails; a duplicate key of concurrent provisioning is retried by the caller
     */
    void provisionSlots(JdbcSession session, String namespace, int limit) throws SQLException;

    /**
     * Rejects a key the database could not keep apart from another one. Keys are exact on every built-in dialect
     * (binary collations, 06 §2.1), except that SQL Server ignores trailing spaces when it compares strings, whatever
     * the collation: there a key ending with a space would silently share the row of the key without it.
     *
     * @param what  what the key is, for the message: {@code range key}, {@code namespace}, {@code owner key}
     * @param key   the key
     * @throws IllegalArgumentException if the database cannot store the key apart from its neighbours
     */
    default void checkKey(String what, String key) {
    }

    /**
     * Whether a failure is a duplicate key, as two concurrent inserts of one key give.
     *
     * @param e the failure
     * @return true for SQL state class {@code 23}
     */
    default boolean isDuplicateKey(SQLException e) {
        String state = e.getSQLState();
        return state != null && state.startsWith("23");
    }

    /**
     * Whether a failed statement may simply run again: a deadlock victim or a serialization failure.
     *
     * @param e the failure
     * @return true for SQL states {@code 40001} and {@code 40P01}
     */
    default boolean isRetryable(SQLException e) {
        String state = e.getSQLState();
        return "40001".equals(state) || "40P01".equals(state);
    }

    /**
     * Whether a failure is the statement running out of the query timeout ({@code StoreConfig.timeout}); the store
     * then fails with {@link me.ahoo.cosid.store.StoreTimeoutException}.
     *
     * @param e the failure
     * @return true for a {@link SQLTimeoutException} and the SQL states {@code 57014} (query canceled: PostgreSQL, H2)
     *         and {@code HY008} (operation canceled: SQL Server)
     */
    default boolean isTimeout(SQLException e) {
        String state = e.getSQLState();
        return e instanceof SQLTimeoutException || "57014".equals(state) || "HY008".equals(state);
    }
}
