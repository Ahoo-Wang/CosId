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

package me.ahoo.cosid.migration.source;

import me.ahoo.cosid.migration.StoreLocation;
import me.ahoo.cosid.store.jdbc.JdbcDialects;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The 4.x JDBC store (09 §4.1): segments in {@code cosid(name = '{namespace}.{name}', last_max_id)}, machine ids in
 * {@code cosid_machine(namespace, ...)}. Options {@code segmentTable} and {@code machineTable} name other tables.
 */
public final class JdbcSource implements SourceReader {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");
    /** The timestamp columns of {@code cosid_machine} (3.x and 4.x), all epoch milliseconds. */
    private static final Set<String> TIMESTAMPS = Set.of("last_timestamp", "renew_time", "watermark", "distribute_time", "revert_time");
    private final StoreLocation location;
    private final String segmentTable;
    private final String machineTable;

    public JdbcSource(StoreLocation location) {
        this.location = location;
        this.segmentTable = table(location, "segmentTable", "cosid");
        this.machineTable = table(location, "machineTable", "cosid_machine");
    }

    static String table(StoreLocation location, String option, String fallback) {
        String name = location.option(option);
        if (name == null) {
            return fallback;
        }
        if (!IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Option " + option + " must be a table name: " + name);
        }
        return name;
    }

    static Connection connect(StoreLocation location) throws SQLException {
        Properties properties = new Properties();
        if (location.user() != null) {
            properties.setProperty("user", location.user());
        }
        if (location.option("password") != null) {
            properties.setProperty("password", location.option("password"));
        }
        return DriverManager.getConnection(location.url(), properties);
    }

    @Override
    public SourceSnapshot read(String namespace) {
        String prefix = namespace + ".";
        Map<String, Long> segments = new TreeMap<>();
        Map<String, String> machines = new TreeMap<>();
        List<String> unrecognized = new ArrayList<>();
        long maxTimestamp = 0;
        int maxMachineId = -1;
        try (Connection connection = connect(location)) {
            try (PreparedStatement select = connection.prepareStatement("select name, last_max_id from " + segmentTable);
                 ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    if (name != null && name.startsWith(prefix)) {
                        segments.put(name.substring(prefix.length()), rs.getLong(2));
                    }
                }
            } catch (SQLException e) {
                if (!isMissingTable(e)) {
                    throw e;
                }
                // A deployment of machine ids only has no segment table.
            }
            try (PreparedStatement select = connection.prepareStatement("select * from " + machineTable + " where namespace = ?")) {
                select.setString(1, namespace);
                try (ResultSet rs = select.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    while (rs.next()) {
                        StringBuilder content = new StringBuilder();
                        String id = null;
                        for (int column = 1; column <= meta.getColumnCount(); column++) {
                            String label = meta.getColumnLabel(column).toLowerCase(Locale.ROOT);
                            String value = rs.getString(column);
                            content.append(label).append('=').append(value).append(';');
                            if ("name".equals(label)) {
                                id = value;
                            }
                            if ("machine_id".equals(label) && value != null) {
                                maxMachineId = Math.max(maxMachineId, rs.getInt(column));
                            }
                            if (TIMESTAMPS.contains(label) && value != null) {
                                maxTimestamp = Math.max(maxTimestamp, rs.getLong(column));
                            }
                        }
                        machines.put(id == null ? String.valueOf(machines.size()) : id, content.toString());
                    }
                }
            } catch (SQLException e) {
                if (!isMissingTable(e)) {
                    throw e;
                }
                // A deployment of segments only has no machine table.
            }
        } catch (SQLException e) {
            SQLException failure = StoreLocation.redacted(e, location.url());
            throw new IllegalStateException("Reading the 4.x JDBC store " + StoreLocation.redactUrl(location.url()) + " failed: " + failure.getMessage(), failure);
        }
        return new SourceSnapshot(segments, machines, maxTimestamp, unrecognized, maxMachineId);
    }

    static boolean isMissingTable(SQLException e) {
        String state = e.getSQLState();
        return state != null && (state.startsWith("42") || "S0002".equals(state));
    }

    @Override
    public long seal(String namespace, String name, long increment) {
        String key = namespace + "." + name;
        try (Connection connection = connect(location)) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                long current;
                try (PreparedStatement select = connection.prepareStatement("select last_max_id from " + segmentTable + " where name = ?")) {
                    select.setString(1, key);
                    try (ResultSet rs = select.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("Segment [" + key + "] does not exist in " + segmentTable);
                        }
                        current = rs.getLong(1);
                    }
                }
                SourceReader.sealed(key, current, increment);
                try (PreparedStatement update = connection.prepareStatement("update " + segmentTable + " set last_max_id = last_max_id + ? where name = ?")) {
                    update.setLong(1, increment);
                    update.setString(2, key);
                    update.executeUpdate();
                }
                long sealed;
                // The update holds the row lock: the value read back includes every fetch that came before it.
                try (PreparedStatement select = connection.prepareStatement("select last_max_id from " + segmentTable + " where name = ?")) {
                    select.setString(1, key);
                    try (ResultSet rs = select.executeQuery()) {
                        rs.next();
                        sealed = rs.getLong(1);
                    }
                }
                // R1-jdbc-06: the update added the increment to what was read, under the row lock; a value below that was
                // not read from the row it wrote (an SQL proxy that routed the statements apart).
                if (sealed < current + increment) {
                    throw new IllegalStateException("Segment [" + key + "] read back last_max_id " + sealed + " after adding " + increment + " to "
                        + current + ": the statements did not reach one database (an SQL proxy?); connect to the primary directly");
                }
                connection.commit();
                return sealed;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            SQLException failure = StoreLocation.redacted(e, location.url());
            throw new IllegalStateException("Sealing segment [" + key + "] failed: " + failure.getMessage(), failure);
        }
    }

    @Override
    public long now() {
        try (Connection connection = connect(location)) {
            String now = JdbcDialects.detect(connection, JdbcSource.class.getClassLoader()).nowMillis();
            try (PreparedStatement select = connection.prepareStatement("select " + now); ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException | IllegalArgumentException e) {
            // A database no 5.0 dialect knows: W rests on the target clock alone.
            return 0;
        }
    }

    @Override
    public void close() {
    }
}
