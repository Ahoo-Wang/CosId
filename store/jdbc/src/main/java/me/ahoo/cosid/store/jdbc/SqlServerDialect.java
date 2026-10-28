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
import java.util.List;
import java.util.OptionalLong;

/**
 * SQL Server 2019+: {@code merge … with (holdlock) … output inserted.next_value}, one round trip; candidate rows
 * with {@code top}.
 */
final class SqlServerDialect implements JdbcDialect {
    @Override
    public String name() {
        return "sqlserver";
    }

    @Override
    public boolean supports(DatabaseMetaData metaData) throws SQLException {
        return JdbcDialects.productIs(metaData, "sql server");
    }

    @Override
    public List<String> schema(JdbcTables tables) {
        return JdbcDialects.schema("schema-sqlserver.sql", tables);
    }

    @Override
    public String nowMillis() {
        return "datediff_big(ms, '1970-01-01', sysutcdatetime())";
    }

    @Override
    public OptionalLong reserve(JdbcSession session, String key, long size, long initialOffset) throws SQLException {
        try (PreparedStatement merge = session.prepare("merge into " + session.tables().range() + " with (holdlock) as r"
            + " using (select ? as range_key) as k on r.range_key = k.range_key"
            + " when matched and r.sealed = 0 and r.next_value <= ? then update set next_value = r.next_value + ?"
            + " when not matched then insert (range_key, next_value) values (k.range_key, ?)"
            + " output inserted.next_value;")) {
            merge.setString(1, key);
            merge.setLong(2, Long.MAX_VALUE - size);
            merge.setLong(3, size);
            merge.setLong(4, initialOffset + size);
            try (ResultSet rs = merge.executeQuery()) {
                return rs.next() ? OptionalLong.of(rs.getLong(1)) : OptionalLong.empty();
            }
        }
    }

    @Override
    public String firstRows(String columns, String table, String where, String orderBy, int rows) {
        return "select top " + rows + " " + columns + " from " + table + " where " + where + " order by " + orderBy;
    }

    @Override
    public boolean insertNamespaceIfAbsent(JdbcSession session, String namespace, int limit, long toleranceMillis) throws SQLException {
        try (PreparedStatement merge = session.prepare("merge into " + session.tables().namespace() + " with (holdlock) as n"
            + " using (select ? as namespace, ? as slot_limit, ? as tolerance_ms) as v on n.namespace = v.namespace"
            + " when not matched then insert (namespace, slot_limit, tolerance_ms) values (v.namespace, v.slot_limit, v.tolerance_ms);")) {
            merge.setString(1, namespace);
            merge.setInt(2, limit);
            merge.setLong(3, toleranceMillis);
            return merge.executeUpdate() > 0;
        }
    }

    /**
     * Without {@code holdlock}: concurrent first uses of a namespace would queue on its key-range locks; instead the
     * loser of a race fails with a duplicate key and the store runs it again, inserting only what is still missing.
     */
    @Override
    public void provisionSlots(JdbcSession session, String namespace, int limit) throws SQLException {
        try (PreparedStatement merge = session.prepare("with n (slot) as (select 0 union all select slot + 1 from n where slot + 1 < ?)"
            + " merge into " + session.tables().slot() + " as s using (select ? as namespace, slot from n) as v"
            + " on s.namespace = v.namespace and s.slot = v.slot"
            + " when not matched then insert (namespace, slot) values (v.namespace, v.slot) option (maxrecursion 0);")) {
            merge.setInt(1, limit);
            merge.setString(2, namespace);
            merge.executeUpdate();
        }
    }

    /**
     * SQL Server pads strings with spaces before it compares them, under every collation: {@code 'a'} and
     * {@code 'a '} are one primary key. Such keys are rejected rather than merged.
     */
    @Override
    public void checkKey(String what, String key) {
        if (key.endsWith(" ")) {
            throw new IllegalArgumentException("SQL Server cannot store a " + what + " that ends with a space apart from the same "
                + what + " without it: [" + key + "]");
        }
    }

    /**
     * Also error 1205 (deadlock victim), whatever SQL state the driver reports for it.
     */
    @Override
    public boolean isRetryable(SQLException e) {
        return e.getErrorCode() == 1205 || JdbcDialect.super.isRetryable(e);
    }
}
