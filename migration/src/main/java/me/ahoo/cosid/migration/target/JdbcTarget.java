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

package me.ahoo.cosid.migration.target;

import me.ahoo.cosid.migration.StoreLocation;
import me.ahoo.cosid.store.jdbc.JdbcDialect;
import me.ahoo.cosid.store.jdbc.JdbcDialects;
import me.ahoo.cosid.store.jdbc.JdbcStore;
import me.ahoo.cosid.store.jdbc.JdbcTables;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * The 5.0 JDBC schema (06 §2.1): {@code cosid_range}, {@code cosid_slot}, {@code cosid_namespace}, under the names of
 * the options {@code rangeTable} / {@code slotTable} / {@code namespaceTable}; {@code initSchema=true} creates missing
 * tables with the dialect's {@code schema-*.sql}. One connection for the whole command, in auto-commit mode.
 */
public final class JdbcTarget implements TargetAdmin {
    private final Connection connection;
    private final JdbcDialect dialect;
    private final JdbcTables tables;
    /** Whether the namespace table has {@code slot_offset}; null until probed. */
    private Boolean slotOffsetColumn;

    public JdbcTarget(StoreLocation location) {
        try {
            Properties properties = new Properties();
            if (location.option("user") != null) {
                properties.setProperty("user", location.option("user"));
            }
            if (location.option("password") != null) {
                properties.setProperty("password", location.option("password"));
            }
            this.connection = DriverManager.getConnection(location.url(), properties);
        } catch (SQLException e) {
            throw new IllegalStateException("Connecting to the 5.0 JDBC store " + location.url() + " failed: " + e.getMessage(), e);
        }
        try {
            ClassLoader classLoader = JdbcTarget.class.getClassLoader();
            String dialectName = location.option("dialect");
            this.dialect = dialectName == null ? JdbcDialects.detect(connection, classLoader)
                : JdbcDialects.named(dialectName, classLoader).orElseThrow(() -> new IllegalArgumentException("Unknown JDBC dialect " + dialectName));
            this.tables = JdbcTables.of(new HashMap<>(location.options()));
            if (Boolean.parseBoolean(location.option("initSchema"))) {
                for (String ddl : dialect.schema(tables)) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute(ddl);
                    }
                }
                // A namespace table of 5.0.0-alpha.1 gets the column, as JdbcStore.initSchema adds it.
                if (!hasSlotOffset()) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute(dialect.addColumn(tables.namespace(), "slot_offset", "integer not null default 0"));
                    }
                    slotOffsetColumn = null;
                }
                // So does the slot table its index of an acquire's own rows (R1-jdbc-01).
                JdbcStore.addMissingIndex(connection, dialect, tables);
            }
        } catch (SQLException | RuntimeException e) {
            close();
            throw new IllegalStateException("Preparing the 5.0 JDBC store failed: " + e.getMessage(), e);
        }
    }

    @Override
    public long now() {
        return query("now", "select " + dialect.nowMillis(), statement -> {
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        });
    }

    @Override
    public Optional<RangeState> range(String key) {
        return query("read range " + key, "select next_value, sealed from " + tables.range() + " where range_key = ?", statement -> {
            statement.setString(1, key);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(new RangeState(key, rs.getLong(1), rs.getBoolean(2))) : Optional.empty();
            }
        });
    }

    @Override
    public RangeState raise(String key, long next) {
        for (int attempt = 0; ; attempt++) {
            int updated = query("raise " + key, "update " + tables.range() + " set next_value = ? where range_key = ? and next_value < ?", statement -> {
                statement.setLong(1, next);
                statement.setString(2, key);
                statement.setLong(3, next);
                return statement.executeUpdate();
            });
            if (updated == 0 && range(key).isEmpty()) {
                try (PreparedStatement insert = connection.prepareStatement("insert into " + tables.range() + " (range_key, next_value, sealed) values (?, ?, ?)")) {
                    insert.setString(1, key);
                    insert.setLong(2, next);
                    insert.setBoolean(3, false);
                    insert.executeUpdate();
                } catch (SQLException e) {
                    if (!dialect.isDuplicateKey(e) || attempt > 3) {
                        throw new IllegalStateException("Creating range " + key + " failed: " + e.getMessage(), e);
                    }
                    continue;
                }
            }
            return range(key).orElseThrow();
        }
    }

    @Override
    public List<RangeState> ranges(String prefix) {
        return query("list ranges", "select range_key, next_value, sealed from " + tables.range() + " order by range_key", statement -> {
            List<RangeState> ranges = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString(1);
                    if (key.startsWith(prefix)) {
                        ranges.add(new RangeState(key, rs.getLong(2), rs.getBoolean(3)));
                    }
                }
            }
            return ranges;
        });
    }

    @Override
    public boolean delete(String key) {
        return query("delete " + key, "delete from " + tables.range() + " where range_key = ? and sealed = ?", statement -> {
            statement.setString(1, key);
            statement.setBoolean(2, true);
            return statement.executeUpdate() > 0;
        });
    }

    @Override
    public Optional<NamespaceState> namespace(String namespace) {
        Optional<long[]> meta = meta(namespace);
        if (meta.isEmpty()) {
            return Optional.empty();
        }
        int limit = (int) meta.get()[0];
        long tolerance = meta.get()[1];
        int slotOffset = (int) meta.get()[2];
        return Optional.of(query("read slots of " + namespace, "select count(*), min(next_floor_ms), sum(case when owner_key is not null and expires_at >= "
            + dialect.nowMillis() + " then 1 else 0 end) from " + tables.slot() + " where namespace = ? and slot < ?", statement -> {
                statement.setString(1, namespace);
                statement.setInt(2, limit);
                try (ResultSet rs = statement.executeQuery()) {
                    rs.next();
                    int slots = rs.getInt(1);
                    return new NamespaceState(limit, tolerance, slotOffset, slots, slots == 0 ? Long.MAX_VALUE : rs.getLong(2), rs.getInt(3));
                }
            }));
    }

    /** {@code {limit, tolerance, slotOffset}}; a table of 5.0.0-alpha.1 has no {@code slot_offset} column: offset 0. */
    private Optional<long[]> meta(String namespace) {
        if (hasSlotOffset()) {
            return query("read namespace " + namespace, "select slot_limit, tolerance_ms, slot_offset from " + tables.namespace() + " where namespace = ?",
                statement -> {
                    statement.setString(1, namespace);
                    try (ResultSet rs = statement.executeQuery()) {
                        return rs.next() ? Optional.of(new long[]{rs.getInt(1), rs.getLong(2), rs.getInt(3)}) : Optional.empty();
                    }
                });
        }
        return query("read namespace " + namespace, "select slot_limit, tolerance_ms from " + tables.namespace() + " where namespace = ?", statement -> {
            statement.setString(1, namespace);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(new long[]{rs.getInt(1), rs.getLong(2), 0}) : Optional.empty();
            }
        });
    }

    /** Whether the namespace table has the {@code slot_offset} column, added after 5.0.0-alpha.1. */
    private boolean hasSlotOffset() {
        if (slotOffsetColumn == null) {
            try (PreparedStatement probe = connection.prepareStatement("select slot_offset from " + tables.namespace() + " where 1 = 0")) {
                probe.executeQuery().close();
                slotOffsetColumn = true;
            } catch (SQLException e) {
                slotOffsetColumn = false;
            }
        }
        return slotOffsetColumn;
    }

    @Override
    public NamespaceState prepare(String namespace, int limit, long toleranceMillis, int slotOffset, long nextFloor) {
        Optional<long[]> meta = meta(namespace);
        if (meta.isEmpty()) {
            if (slotOffset != 0 && !hasSlotOffset()) {
                throw new IllegalStateException("Namespace " + namespace + " needs slot offset " + slotOffset + ", but " + tables.namespace()
                    + " has no slot_offset column (a table of 5.0.0-alpha.1): add it with [alter table " + tables.namespace()
                    + " add slot_offset integer not null default 0], or run with initSchema");
            }
            // Offset 0 leaves the column to its default, as 5.0.0-alpha.1 wrote it.
            String sql = slotOffset == 0 ? "insert into " + tables.namespace() + " (namespace, slot_limit, tolerance_ms) values (?, ?, ?)"
                : "insert into " + tables.namespace() + " (namespace, slot_limit, tolerance_ms, slot_offset) values (?, ?, ?, ?)";
            try (PreparedStatement insert = connection.prepareStatement(sql)) {
                insert.setString(1, namespace);
                insert.setInt(2, limit);
                insert.setLong(3, toleranceMillis);
                if (slotOffset != 0) {
                    insert.setInt(4, slotOffset);
                }
                insert.executeUpdate();
            } catch (SQLException e) {
                if (!dialect.isDuplicateKey(e)) {
                    throw new IllegalStateException("Recording namespace " + namespace + " failed: " + e.getMessage(), e);
                }
            }
            meta = meta(namespace);
        }
        meta.orElseThrow();
        NamespaceState before = namespace(namespace).orElseThrow();
        if (!TargetAdmin.matches(before, limit, toleranceMillis, slotOffset)) {
            throw TargetAdmin.mismatch(namespace, limit, toleranceMillis, slotOffset, before);
        }
        if (before.live() > 0) {
            throw TargetAdmin.live(namespace, before.live());
        }
        Set<Integer> existing = query("list slots of " + namespace, "select slot from " + tables.slot() + " where namespace = ? and slot < ?", statement -> {
            statement.setString(1, namespace);
            statement.setInt(2, limit);
            Set<Integer> slots = new HashSet<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    slots.add(rs.getInt(1));
                }
            }
            return slots;
        });
        try (PreparedStatement insert = connection.prepareStatement("insert into " + tables.slot() + " (namespace, slot, next_floor_ms) values (?, ?, ?)")) {
            int batched = 0;
            for (int slot = 0; slot < limit; slot++) {
                if (existing.contains(slot)) {
                    continue;
                }
                insert.setString(1, namespace);
                insert.setInt(2, slot);
                insert.setLong(3, nextFloor);
                insert.addBatch();
                if (++batched % 500 == 0) {
                    insert.executeBatch();
                }
            }
            if (batched % 500 != 0) {
                insert.executeBatch();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Creating the slots of " + namespace + " failed: " + e.getMessage(), e);
        }
        query("raise slots of " + namespace, "update " + tables.slot() + " set next_floor_ms = ? where namespace = ? and slot < ? and next_floor_ms < ?", statement -> {
            statement.setLong(1, nextFloor);
            statement.setString(2, namespace);
            statement.setInt(3, limit);
            statement.setLong(4, nextFloor);
            return statement.executeUpdate();
        });
        return namespace(namespace).orElseThrow();
    }

    private <T> T query(String operation, String sql, Work<T> work) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            return work.run(statement);
        } catch (SQLException e) {
            throw new IllegalStateException(operation + " failed on the 5.0 JDBC store: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            // closing anyway
        }
    }

    @FunctionalInterface
    private interface Work<T> {
        T run(PreparedStatement statement) throws SQLException;
    }
}
