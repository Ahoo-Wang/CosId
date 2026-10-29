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
 * H2 2.x, for development and tests: a {@code merge … using} upsert, then a read of the row in the same transaction,
 * which still holds the row lock of the merge.
 */
final class H2Dialect implements JdbcDialect {
    private final TableSql mergeSql = new TableSql(tables -> "merge into " + tables.range() + " r using (values (cast(? as varchar(255)))) k(range_key)"
        + " on r.range_key = k.range_key"
        + " when matched and not r.sealed and r.next_value <= ? then update set next_value = r.next_value + ?"
        + " when not matched then insert (range_key, next_value) values (k.range_key, ?)");
    private final TableSql nextSql = new TableSql(tables -> "select next_value from " + tables.range() + " where range_key = ?");

    @Override
    public String name() {
        return "h2";
    }

    @Override
    public boolean supports(DatabaseMetaData metaData) throws SQLException {
        return "h2".equalsIgnoreCase(metaData.getDatabaseProductName());
    }

    @Override
    public List<String> schema(JdbcTables tables) {
        return JdbcDialects.schema("schema-h2.sql", tables);
    }

    @Override
    public String nowMillis() {
        return "cast(extract(epoch from current_timestamp) * 1000 as bigint)";
    }

    @Override
    public OptionalLong reserve(JdbcSession session, String key, long size, long initialOffset) throws SQLException {
        JdbcTables tables = session.tables();
        return session.transaction(() -> {
            int updated;
            try (PreparedStatement merge = session.prepare(mergeSql.of(tables))) {
                merge.setString(1, key);
                merge.setLong(2, Long.MAX_VALUE - size);
                merge.setLong(3, size);
                merge.setLong(4, initialOffset + size);
                updated = merge.executeUpdate();
            }
            if (updated == 0) {
                return OptionalLong.empty();
            }
            try (PreparedStatement select = session.prepare(nextSql.of(tables))) {
                select.setString(1, key);
                try (ResultSet rs = select.executeQuery()) {
                    rs.next();
                    return OptionalLong.of(rs.getLong(1));
                }
            }
        });
    }

    @Override
    public void provisionSlots(JdbcSession session, String namespace, int limit) throws SQLException {
        try (PreparedStatement merge = session.prepare("merge into " + session.tables().slot() + " s"
            + " using (select cast(? as varchar(100)) namespace, cast(x as integer) slot from system_range(0, ?)) v"
            + " on s.namespace = v.namespace and s.slot = v.slot"
            + " when not matched then insert (namespace, slot) values (v.namespace, v.slot)")) {
            merge.setString(1, namespace);
            merge.setInt(2, limit - 1);
            merge.executeUpdate();
        }
    }
}
