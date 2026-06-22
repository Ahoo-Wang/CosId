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

import static me.ahoo.cosid.machine.MachineIdDistributor.namespacedMachineId;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

final class InMemoryJdbcDataSource implements DataSource {
    private final Map<String, Long> segments = new ConcurrentHashMap<>();
    private final Map<String, MachineRow> machineRows = new LinkedHashMap<>();
    private final List<String> executedSql = Collections.synchronizedList(new ArrayList<>());
    private final ReentrantLock transactionLock = new ReentrantLock();
    private volatile int loginTimeout;
    private PrintWriter logWriter;
    private int failedRevertDistributeUpdates;
    private boolean cosIdTableInitialized;
    private boolean cosIdMachineTableInitialized;

    @Override
    public Connection getConnection() {
        return connection();
    }

    @Override
    public Connection getConnection(String username, String password) {
        return getConnection();
    }

    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        logWriter = out;
    }

    @Override
    public void setLoginTimeout(int seconds) {
        loginTimeout = seconds;
    }

    @Override
    public int getLoginTimeout() {
        return loginTimeout;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Not a wrapper for " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }

    synchronized void setSegmentMaxId(String namespacedName, long maxId) {
        requireSegment(namespacedName);
        segments.put(namespacedName, maxId);
    }

    synchronized long getSegmentMaxId(String namespacedName) {
        return segments.get(namespacedName);
    }

    synchronized boolean containsSegment(String namespacedName) {
        return segments.containsKey(namespacedName);
    }

    synchronized void putMachine(String namespace, int machineId, String instanceId, long lastTimestamp) {
        String name = namespacedMachineId(namespace, machineId);
        machineRows.put(name, new MachineRow(name, namespace, machineId, lastTimestamp, instanceId, System.currentTimeMillis(), 0));
    }

    synchronized Optional<MachineRowSnapshot> findMachine(String namespace, int machineId) {
        MachineRow row = machineRows.get(namespacedMachineId(namespace, machineId));
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(row.snapshot());
    }

    synchronized void failNextRevertDistributeUpdates(int times) {
        failedRevertDistributeUpdates = times;
    }

    boolean isCosIdTableInitialized() {
        return cosIdTableInitialized;
    }

    boolean isCosIdMachineTableInitialized() {
        return cosIdMachineTableInitialized;
    }

    List<String> getExecutedSql() {
        synchronized (executedSql) {
            return List.copyOf(executedSql);
        }
    }

    private Connection connection() {
        return proxy(Connection.class, new InvocationHandler() {
            private final AtomicBoolean transactionLocked = new AtomicBoolean();

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String methodName = method.getName();
                if ("prepareStatement".equals(methodName)) {
                    return preparedStatement((String) args[0]);
                }
                if ("setAutoCommit".equals(methodName)) {
                    boolean autoCommit = (Boolean) args[0];
                    if (!autoCommit && transactionLocked.compareAndSet(false, true)) {
                        transactionLock.lock();
                    }
                    if (autoCommit) {
                        unlockTransaction();
                    }
                    return null;
                }
                if ("commit".equals(methodName) || "rollback".equals(methodName) || "close".equals(methodName)) {
                    unlockTransaction();
                    return null;
                }
                if ("getAutoCommit".equals(methodName)) {
                    return !transactionLocked.get();
                }
                if ("isClosed".equals(methodName)) {
                    return false;
                }
                if ("unwrap".equals(methodName)) {
                    return unwrapArgument(proxy, args);
                }
                if ("isWrapperFor".equals(methodName)) {
                    return ((Class<?>) args[0]).isInstance(proxy);
                }
                return defaultValue(method.getReturnType());
            }

            private void unlockTransaction() {
                if (transactionLocked.getAndSet(false)) {
                    transactionLock.unlock();
                }
            }
        });
    }

    private PreparedStatement preparedStatement(String sql) {
        executedSql.add(sql);
        Map<Integer, Object> params = new HashMap<>();
        return proxy(PreparedStatement.class, (proxy, method, args) -> {
            String methodName = method.getName();
            if (methodName.startsWith("set")) {
                params.put((Integer) args[0], args[1]);
                return null;
            }
            if ("executeUpdate".equals(methodName)) {
                return executeUpdate(sql, params);
            }
            if ("executeQuery".equals(methodName)) {
                return executeQuery(sql, params);
            }
            if ("close".equals(methodName)) {
                return null;
            }
            if ("unwrap".equals(methodName)) {
                return unwrapArgument(proxy, args);
            }
            if ("isWrapperFor".equals(methodName)) {
                return ((Class<?>) args[0]).isInstance(proxy);
            }
            return defaultValue(method.getReturnType());
        });
    }

    private ResultSet resultSet(List<Row> rows) {
        return proxy(ResultSet.class, new InvocationHandler() {
            private int index = -1;

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws SQLException {
                String methodName = method.getName();
                if ("next".equals(methodName)) {
                    index++;
                    return index < rows.size();
                }
                if ("getLong".equals(methodName)) {
                    return current().getLong((Integer) args[0]);
                }
                if ("getInt".equals(methodName)) {
                    return current().getInt((Integer) args[0]);
                }
                if ("close".equals(methodName)) {
                    return null;
                }
                if ("unwrap".equals(methodName)) {
                    return unwrapArgument(proxy, args);
                }
                if ("isWrapperFor".equals(methodName)) {
                    return ((Class<?>) args[0]).isInstance(proxy);
                }
                return defaultValue(method.getReturnType());
            }

            private Row current() throws SQLException {
                if (index < 0 || index >= rows.size()) {
                    throw new SQLException("ResultSet cursor is not positioned on a row.");
                }
                return rows.get(index);
            }
        });
    }

    private synchronized int executeUpdate(String sql, Map<Integer, Object> params) throws SQLException {
        String normalizedSql = normalize(sql);
        if (normalizedSql.equals(normalize(JdbcIdSegmentInitializer.INIT_COSID_TABLE_SQL))) {
            cosIdTableInitialized = true;
            return 0;
        }
        if (normalizedSql.equals(normalize(JdbcIdSegmentInitializer.INIT_ID_SEGMENT_SQL))) {
            return initSegment((String) params.get(1), (Long) params.get(2));
        }
        if (normalizedSql.equals(normalize(JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL))) {
            return incrementSegment((String) params.get(2), (Long) params.get(1));
        }
        if (normalizedSql.startsWith("create table if not exists cosid_machine")) {
            cosIdMachineTableInitialized = true;
            return 0;
        }
        if (normalizedSql.startsWith("create index if not exists idx_namespace")
            || normalizedSql.startsWith("create index if not exists idx_instance_id")) {
            return 0;
        }
        if (normalizedSql.startsWith("update cosid_machine set instance_id=?,last_timestamp=?,distribute_time=? where name=?")) {
            return distributeRevertedMachine((String) params.get(4), (String) params.get(1), (Long) params.get(2), (Long) params.get(3), (Long) params.get(5));
        }
        if (normalizedSql.startsWith("insert into cosid_machine")) {
            return insertMachine(params);
        }
        if (normalizedSql.startsWith("update cosid_machine set instance_id=?,last_timestamp=?,revert_time=? where namespace=?")) {
            return revertMachine((String) params.get(4), (String) params.get(5), (String) params.get(1), (Long) params.get(2), (Long) params.get(3));
        }
        if (normalizedSql.startsWith("update cosid_machine set last_timestamp=? where namespace=?")) {
            return guardMachine((String) params.get(2), (String) params.get(3), (Integer) params.get(4), (Long) params.get(1));
        }
        throw unsupportedSql(sql);
    }

    private synchronized ResultSet executeQuery(String sql, Map<Integer, Object> params) throws SQLException {
        String normalizedSql = normalize(sql);
        if (normalizedSql.equals(normalize(JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL))) {
            Long lastMaxId = segments.get((String) params.get(1));
            if (lastMaxId == null) {
                return resultSet(List.of());
            }
            return resultSet(List.of(Row.of(lastMaxId)));
        }
        if (normalizedSql.startsWith("select machine_id, last_timestamp from cosid_machine where namespace=? and instance_id=?")) {
            return resultSet(findSelfMachineRows((String) params.get(1), (String) params.get(2), (Long) params.get(3)));
        }
        if (normalizedSql.startsWith("select machine_id, last_timestamp from cosid_machine where namespace=? and (instance_id=''")) {
            return resultSet(findRevertibleMachineRows((String) params.get(1), (Long) params.get(2)));
        }
        if (normalizedSql.startsWith("select max(machine_id)+1 as next_machine_id from cosid_machine where namespace=?")) {
            return resultSet(List.of(Row.of(nextMachineId((String) params.get(1)))));
        }
        throw unsupportedSql(sql);
    }

    private int initSegment(String segmentName, long offset) throws SQLIntegrityConstraintViolationException {
        if (segments.containsKey(segmentName)) {
            throw new SQLIntegrityConstraintViolationException("Duplicate segment: " + segmentName);
        }
        segments.put(segmentName, offset);
        return 1;
    }

    private int incrementSegment(String segmentName, long step) {
        Long current = segments.get(segmentName);
        if (current == null) {
            return 0;
        }
        segments.put(segmentName, current + step);
        return 1;
    }

    private int distributeRevertedMachine(String name, String instanceId, long lastTimestamp, long distributeTime, long safeGuardAt) {
        MachineRow row = machineRows.get(name);
        if (row == null || (!row.instanceId.isEmpty() && row.lastTimestamp > safeGuardAt)) {
            return 0;
        }
        if (failedRevertDistributeUpdates > 0) {
            failedRevertDistributeUpdates--;
            return 0;
        }
        row.instanceId = instanceId;
        row.lastTimestamp = lastTimestamp;
        row.distributeTime = distributeTime;
        return 1;
    }

    private int insertMachine(Map<Integer, Object> params) throws SQLIntegrityConstraintViolationException {
        String name = (String) params.get(1);
        if (machineRows.containsKey(name)) {
            throw new SQLIntegrityConstraintViolationException("Duplicate machine: " + name);
        }
        machineRows.put(name, new MachineRow(
            name,
            (String) params.get(2),
            (Integer) params.get(3),
            (Long) params.get(4),
            (String) params.get(5),
            (Long) params.get(6),
            0
        ));
        return 1;
    }

    private int revertMachine(String namespace, String oldInstanceId, String nextInstanceId, long lastTimestamp, long revertTime) {
        int affected = 0;
        for (MachineRow row : machineRows.values()) {
            if (row.namespace.equals(namespace) && row.instanceId.equals(oldInstanceId)) {
                row.instanceId = nextInstanceId;
                row.lastTimestamp = lastTimestamp;
                row.revertTime = revertTime;
                affected++;
            }
        }
        return affected;
    }

    private int guardMachine(String namespace, String instanceId, int machineId, long lastTimestamp) {
        MachineRow row = machineRows.get(namespacedMachineId(namespace, machineId));
        if (row == null || !row.namespace.equals(namespace) || !row.instanceId.equals(instanceId)) {
            return 0;
        }
        row.lastTimestamp = lastTimestamp;
        return 1;
    }

    private List<Row> findSelfMachineRows(String namespace, String instanceId, long safeGuardAt) {
        return machineRows.values()
            .stream()
            .filter(row -> row.namespace.equals(namespace))
            .filter(row -> row.instanceId.equals(instanceId))
            .filter(row -> row.lastTimestamp > safeGuardAt)
            .sorted(Comparator.comparingInt(row -> row.machineId))
            .map(row -> Row.of(row.machineId, row.lastTimestamp))
            .toList();
    }

    private List<Row> findRevertibleMachineRows(String namespace, long safeGuardAt) {
        return machineRows.values()
            .stream()
            .filter(row -> row.namespace.equals(namespace))
            .filter(row -> row.instanceId.isEmpty() || row.lastTimestamp <= safeGuardAt)
            .sorted(Comparator.comparingInt(row -> row.machineId))
            .map(row -> Row.of(row.machineId, row.lastTimestamp))
            .toList();
    }

    private int nextMachineId(String namespace) {
        return machineRows.values()
            .stream()
            .filter(row -> row.namespace.equals(namespace))
            .mapToInt(row -> row.machineId)
            .max()
            .orElse(-1) + 1;
    }

    private void requireSegment(String namespacedName) {
        if (!segments.containsKey(namespacedName)) {
            throw new IllegalArgumentException("Segment does not exist: " + namespacedName);
        }
    }

    private static SQLException unsupportedSql(String sql) {
        return new SQLException("Unsupported SQL: " + sql);
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object unwrapArgument(Object proxy, Object[] args) throws SQLException {
        Class<?> wrapperType = (Class<?>) args[0];
        if (wrapperType.isInstance(proxy)) {
            return wrapperType.cast(proxy);
        }
        throw new SQLException("Not a wrapper for " + wrapperType.getName());
    }

    private static Object defaultValue(Class<?> returnType) {
        if (Boolean.TYPE.equals(returnType)) {
            return false;
        }
        if (Integer.TYPE.equals(returnType)) {
            return 0;
        }
        if (Long.TYPE.equals(returnType)) {
            return 0L;
        }
        if (Void.TYPE.equals(returnType)) {
            return null;
        }
        return null;
    }

    record MachineRowSnapshot(String name, String namespace, int machineId, long lastTimestamp, String instanceId, long distributeTime, long revertTime) {
    }

    private static final class MachineRow {
        private final String name;
        private final String namespace;
        private final int machineId;
        private long lastTimestamp;
        private String instanceId;
        private long distributeTime;
        private long revertTime;

        private MachineRow(String name, String namespace, int machineId, long lastTimestamp, String instanceId, long distributeTime, long revertTime) {
            this.name = name;
            this.namespace = namespace;
            this.machineId = machineId;
            this.lastTimestamp = lastTimestamp;
            this.instanceId = instanceId;
            this.distributeTime = distributeTime;
            this.revertTime = revertTime;
        }

        private MachineRowSnapshot snapshot() {
            return new MachineRowSnapshot(name, namespace, machineId, lastTimestamp, instanceId, distributeTime, revertTime);
        }
    }

    private static final class Row {
        private final Object[] values;

        private Row(Object... values) {
            this.values = values;
        }

        private static Row of(Object... values) {
            return new Row(values);
        }

        private long getLong(int index) {
            Object value = values[index - 1];
            if (value == null) {
                return 0L;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            return Long.parseLong(Objects.toString(value));
        }

        private int getInt(int index) {
            Object value = values[index - 1];
            if (value == null) {
                return 0;
            }
            if (value instanceof Number number) {
                return number.intValue();
            }
            return Integer.parseInt(Objects.toString(value));
        }
    }
}
