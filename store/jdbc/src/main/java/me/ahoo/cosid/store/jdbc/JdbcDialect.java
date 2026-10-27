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
 * next_floor_ms)} and {@code cosid_namespace(namespace, slot_limit, tolerance_ms)}, under the names of
 * {@link JdbcSession#tables()}.
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
     * Records the parameters of a namespace on its first use; a namespace that exists is left as it is.
     *
     * @param session         the session
     * @param namespace       the namespace
     * @param limit           the slot limit
     * @param toleranceMillis the tolerance
     * @return whether this call inserted the row: the one first use that creates the slot rows
     * @throws SQLException if the database fails; a duplicate key counts as "not inserted"
     */
    default boolean insertNamespaceIfAbsent(JdbcSession session, String namespace, int limit, long toleranceMillis) throws SQLException {
        try (PreparedStatement insert = session.prepare("insert into " + session.tables().namespace()
            + " (namespace, slot_limit, tolerance_ms) values (?, ?, ?)")) {
            insert.setString(1, namespace);
            insert.setInt(2, limit);
            insert.setLong(3, toleranceMillis);
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
}
