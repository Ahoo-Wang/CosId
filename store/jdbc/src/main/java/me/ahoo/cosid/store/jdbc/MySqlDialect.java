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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.OptionalLong;

/**
 * MySQL 8.4+ and MariaDB 10.11+.
 *
 * <p>{@code reserve} first runs {@code update … set next_value = last_insert_id(next_value + ?)} on an open key: one
 * statement of 2 parameters for the usual reserve, as 4.x. When it matches no row, or would pass {@code Long.MAX_VALUE}
 * (error 1690, never a wrap), it runs
 * {@code insert … on duplicate key update} where <em>both</em> branches set {@code last_insert_id}: the insert branch
 * through {@code values (?, last_insert_id(? + ?))}, the update branch through {@code last_insert_id(next_value + ?)},
 * or {@code last_insert_id(0)} when the key is sealed or full. Either statement's value is read from its own OK
 * packet ({@code getGeneratedKeys()}, as 4.x does). 0 cannot be a new
 * {@code next_value}, so an empty or 0 key means "not reserved", but only if the row says so (sealed, or full): the
 * store never asks the connection for {@code last_insert_id()}, which an SQL proxy may answer from another backend
 * connection (R1-jdbc-06). A key the statement cannot have set fails the reserve; {@link FetchPlan#TRANSACTION}
 * avoids generated keys altogether.
 */
final class MySqlDialect implements JdbcDialect {
    /** Rows of one provisioning insert: 2 parameters each. */
    private static final int PROVISION_CHUNK = 256;
    /** ER_DATA_OUT_OF_RANGE: a BIGINT expression past its range, on MySQL and MariaDB. */
    private static final int OUT_OF_RANGE = 1690;
    private final TableSql addSql = new TableSql(tables -> "update " + tables.range()
        + " set next_value = last_insert_id(next_value + ?) where range_key = ? and not sealed");
    private final TableSql reserveSql = new TableSql(tables -> "insert into " + tables.range() + " (range_key, next_value) values (?, last_insert_id(? + ?))"
        + " on duplicate key update next_value = if(sealed or next_value > ?, next_value + last_insert_id(0), last_insert_id(next_value + ?))");
    private final boolean mariaDb;

    MySqlDialect(boolean mariaDb) {
        this.mariaDb = mariaDb;
    }

    @Override
    public String name() {
        return mariaDb ? "mariadb" : "mysql";
    }

    @Override
    public boolean supports(DatabaseMetaData metaData) throws SQLException {
        boolean isMariaDb = JdbcDialects.isMariaDb(metaData);
        return mariaDb ? isMariaDb : !isMariaDb && JdbcDialects.productIs(metaData, "mysql");
    }

    @Override
    public List<String> schema(JdbcTables tables) {
        return JdbcDialects.schema(mariaDb ? "schema-mariadb.sql" : "schema-mysql.sql", tables);
    }

    @Override
    public String nowMillis() {
        return "(timestampdiff(microsecond, '1970-01-01 00:00:00', utc_timestamp(6)) div 1000)";
    }

    @Override
    public OptionalLong reserve(JdbcSession session, String key, long size, long initialOffset) throws SQLException {
        // An existing open key, the usual case: one update of 2 parameters, as 4.x (R1-jdbc-04). The upsert below runs
        // only when it matched no row (a key not created yet, or a sealed one) or would pass Long.MAX_VALUE, which
        // MySQL and MariaDB refuse with error 1690 in every SQL mode rather than wrap.
        try (PreparedStatement update = session.prepare(addSql.of(session.tables()), Statement.RETURN_GENERATED_KEYS)) {
            update.setLong(1, size);
            update.setString(2, key);
            if (update.executeUpdate() == 1) {
                try (ResultSet keys = update.getGeneratedKeys()) {
                    if (keys.next() && keys.getLong(1) != 0) {
                        return OptionalLong.of(keys.getLong(1));
                    }
                }
                throw lostKey(key, size);
            }
        } catch (SQLException e) {
            if (e.getErrorCode() != OUT_OF_RANGE) {
                throw e;
            }
        }
        try (PreparedStatement upsert = session.prepare(reserveSql.of(session.tables()), Statement.RETURN_GENERATED_KEYS)) {
            upsert.setString(1, key);
            upsert.setLong(2, initialOffset);
            upsert.setLong(3, size);
            upsert.setLong(4, Long.MAX_VALUE - size);
            upsert.setLong(5, size);
            upsert.executeUpdate();
            // The OK packet of the statement carries the last_insert_id it set: no second round trip.
            try (ResultSet keys = upsert.getGeneratedKeys()) {
                if (keys.next() && keys.getLong(1) != 0) {
                    return OptionalLong.of(keys.getLong(1));
                }
            }
        }
        // last_insert_id(0): the key is sealed or full, as its row shows. Otherwise the generated key was lost on the way.
        try (PreparedStatement select = session.prepare("select sealed, next_value from " + session.tables().range() + " where range_key = ?")) {
            select.setString(1, key);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next() && (rs.getBoolean(1) || rs.getLong(2) > Long.MAX_VALUE - size)) {
                    return OptionalLong.empty();
                }
            }
        }
        throw lostKey(key, size);
    }

    private static IllegalStateException lostKey(String key, long size) {
        return new IllegalStateException("Reserving " + size + " of [" + key + "] returned no generated key, but the key is neither sealed nor full:"
            + " last_insert_id did not reach the store (an SQL proxy?); set options.fetchPlan=transaction");
    }

    @Override
    public boolean insertNamespaceIfAbsent(JdbcSession session, String namespace, int limit, long toleranceMillis, int slotOffset) throws SQLException {
        try (PreparedStatement insert = session.prepare("insert ignore into " + session.tables().namespace() + " " + JdbcDialects.namespaceInsert(slotOffset))) {
            JdbcDialects.bindNamespace(insert, namespace, limit, toleranceMillis, slotOffset);
            return insert.executeUpdate() > 0;
        }
    }

    @Override
    public void provisionSlots(JdbcSession session, String namespace, int limit) throws SQLException {
        for (int from = 0; from < limit; from += PROVISION_CHUNK) {
            int rows = Math.min(PROVISION_CHUNK, limit - from);
            StringBuilder sql = new StringBuilder("insert ignore into ").append(session.tables().slot()).append(" (namespace, slot) values ");
            for (int i = 0; i < rows; i++) {
                sql.append(i == 0 ? "(?, ?)" : ", (?, ?)");
            }
            try (PreparedStatement insert = session.prepare(sql.toString())) {
                for (int i = 0; i < rows; i++) {
                    insert.setString(2 * i + 1, namespace);
                    insert.setInt(2 * i + 2, from + i);
                }
                insert.executeUpdate();
            }
        }
    }
}
