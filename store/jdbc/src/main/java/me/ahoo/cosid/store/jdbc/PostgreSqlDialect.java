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
 * PostgreSQL 15+: {@code insert … on conflict … do update … where … returning}, one round trip.
 */
final class PostgreSqlDialect implements JdbcDialect {
    @Override
    public String name() {
        return "postgresql";
    }

    @Override
    public boolean supports(DatabaseMetaData metaData) throws SQLException {
        return JdbcDialects.productIs(metaData, "postgresql");
    }

    @Override
    public List<String> schema(JdbcTables tables) {
        return JdbcDialects.schema("schema-postgresql.sql", tables);
    }

    @Override
    public String nowMillis() {
        return "(extract(epoch from clock_timestamp()) * 1000)::bigint";
    }

    @Override
    public OptionalLong reserve(JdbcSession session, String key, long size, long initialOffset) throws SQLException {
        String range = session.tables().range();
        try (PreparedStatement upsert = session.prepare("insert into " + range + " as r (range_key, next_value) values (?, ? + ?)"
            + " on conflict (range_key) do update set next_value = r.next_value + ? where not r.sealed and r.next_value <= ?"
            + " returning next_value")) {
            upsert.setString(1, key);
            upsert.setLong(2, initialOffset);
            upsert.setLong(3, size);
            upsert.setLong(4, size);
            upsert.setLong(5, Long.MAX_VALUE - size);
            try (ResultSet rs = upsert.executeQuery()) {
                return rs.next() ? OptionalLong.of(rs.getLong(1)) : OptionalLong.empty();
            }
        }
    }

    @Override
    public boolean insertNamespaceIfAbsent(JdbcSession session, String namespace, int limit, long toleranceMillis) throws SQLException {
        try (PreparedStatement insert = session.prepare("insert into " + session.tables().namespace()
            + " (namespace, slot_limit, tolerance_ms) values (?, ?, ?) on conflict (namespace) do nothing")) {
            insert.setString(1, namespace);
            insert.setInt(2, limit);
            insert.setLong(3, toleranceMillis);
            return insert.executeUpdate() > 0;
        }
    }

    @Override
    public void provisionSlots(JdbcSession session, String namespace, int limit) throws SQLException {
        try (PreparedStatement insert = session.prepare("insert into " + session.tables().slot()
            + " (namespace, slot) select ?, s from generate_series(0, ? - 1) s on conflict (namespace, slot) do nothing")) {
            insert.setString(1, namespace);
            insert.setInt(2, limit);
            insert.executeUpdate();
        }
    }
}
