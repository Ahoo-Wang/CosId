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

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.internal.Checks;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.lease.Lease;
import me.ahoo.cosid.machine.lease.MachineLeaseStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * {@link MachineLeaseStore} on the {@code cosid_machine} table, with conditional updates on
 * {@code (instance_id, last_timestamp)} as the compare-and-set.
 *
 * <p>It uses the same rows as the 3.4 machine id distributor (a released machine id has an empty {@code instance_id}),
 * so instances running either one, and 3.4 instances, can share a namespace during an upgrade. The lease keeps its
 * renewal time, watermark and version in the {@code renew_time}, {@code watermark} and {@code lease_version} columns,
 * and {@code last_timestamp} holds a value that is at least all of them and strictly increases, which 3.4 reads as its
 * last timestamp and which is the lease version. {@code lease_version} equals {@code last_timestamp} when this store
 * wrote the row last; otherwise a 3.4 instance did, and {@code last_timestamp} stands for the renewal time and the
 * watermark. A 3.4 instance can write back the same {@code last_timestamp} (a host clock behind it), so handing a
 * machine id to someone else never relies on the lease columns alone: expiry is judged on {@code last_timestamp},
 * exactly as 3.4 judges it, and a new owner starts after it. The table needs the {@code stable} column ({@link #ADD_STABLE_COLUMN_SQL}) and
 * the lease columns ({@link #ADD_LEASE_COLUMNS_SQL}, {@link PostgreSqlDialect#ADD_LEASE_COLUMNS_SQL}): the constructor
 * fails naming the statement when they are missing. The server clock is read with {@code serverTimeSql}:
 * {@link #MYSQL_SERVER_TIME_SQL} by default, {@link JdbcDialect#getServerTimeSql()} for the other databases. Every statement
 * has the query timeout, and writes are committed when the connection is not in auto-commit mode.</p>
 *
 * @author ahoo wang
 * @since 3.4.0
 */
public class JdbcMachineLeaseStore implements MachineLeaseStore {
    /**
     * Current time of a MySQL server in milliseconds.
     *
     * <p>{@code unix_timestamp()} without an argument does not go through the session time zone: {@code
     * unix_timestamp(now(3))} does, and returns a time one hour too low during the repeated hour at the end of daylight
     * saving time.</p>
     */
    public static final String MYSQL_SERVER_TIME_SQL = "select unix_timestamp() * 1000 + floor(microsecond(now(3)) / 1000)";
    
    /**
     * Fails on tables created before the {@code stable} column existed.
     */
    private static final String PROBE_STABLE_COLUMN = "select stable from cosid_machine where 1=0";
    
    private static final String PROBE_TABLE = "select 1 from cosid_machine where 1=0";
    
    /**
     * Migration of a table created before the {@code stable} column existed (MySQL syntax).
     */
    public static final String ADD_STABLE_COLUMN_SQL = "alter table cosid_machine add column stable tinyint not null default 0";
    
    /**
     * Fails on tables created before 4.0.
     */
    private static final String PROBE_LEASE_COLUMNS = "select renew_time, watermark, lease_version from cosid_machine where 1=0";
    
    /**
     * Migration of a {@code cosid_machine} table created before 4.0 (MySQL syntax).
     */
    public static final String ADD_LEASE_COLUMNS_SQL = "alter table cosid_machine add column renew_time bigint unsigned not null default 0, "
        + "add column watermark bigint unsigned not null default 0, add column lease_version bigint unsigned not null default 0";
    
    private static final String COLUMNS = "machine_id, last_timestamp, instance_id, renew_time, watermark, lease_version";
    
    
    /**
     * Upper bound of attempts when concurrent instances race for the same rows.
     */
    static final int MAX_ATTEMPTS = 32;
    
    private static final String SELECT_SELF =
        "select " + COLUMNS + " from cosid_machine where namespace=? and instance_id=? order by machine_id";
    
    private static final String SELECT_RELEASED =
        "select " + COLUMNS + " from cosid_machine where namespace=? and instance_id='' and machine_id<=? order by machine_id";
    
    private static final String SELECT_EXPIRED =
        "select " + COLUMNS + " from cosid_machine "
            + "where namespace=? and instance_id<>'' and stable=0 and last_timestamp<=? and machine_id<=? order by machine_id";
    
    private static final String SELECT_MAX_MACHINE_ID = "select max(machine_id) from cosid_machine where namespace=?";
    
    private static final String INSERT =
        "insert into cosid_machine (name, namespace, machine_id, last_timestamp, instance_id, distribute_time, revert_time, stable, "
            + "renew_time, watermark, lease_version) values (?,?,?,?,?,?,0,?,?,?,?)";
    
    private static final String TAKE =
        "update cosid_machine set instance_id=?, last_timestamp=?, distribute_time=?, stable=?, renew_time=?, watermark=?, lease_version=? "
            + "where name=? and instance_id=? and last_timestamp=?";
    
    private static final String RENEW =
        "update cosid_machine set last_timestamp=?, stable=?, renew_time=?, lease_version=? where name=? and instance_id=? and last_timestamp=?";
    
    private static final String RELEASE =
        "update cosid_machine set instance_id=?, last_timestamp=?, revert_time=?, stable=?, watermark=?, lease_version=? "
            + "where name=? and instance_id=? and last_timestamp=?";
    
    private final DataSource dataSource;
    private final int queryTimeoutSeconds;
    private final String serverTimeSql;
    
    public JdbcMachineLeaseStore(DataSource dataSource) {
        this(dataSource, JdbcSupport.DEFAULT_QUERY_TIMEOUT, MYSQL_SERVER_TIME_SQL);
    }
    
    /**
     * Create a JDBC lease store.
     *
     * @param queryTimeout  timeout applied to each statement, {@code null} or zero means no timeout
     * @param serverTimeSql a query returning the database server's current time in milliseconds
     */
    public JdbcMachineLeaseStore(DataSource dataSource, Duration queryTimeout, String serverTimeSql) {
        this.dataSource = Checks.checkNotNull(dataSource, "dataSource can not be null!");
        this.queryTimeoutSeconds = JdbcSupport.toQueryTimeoutSeconds(queryTimeout);
        Checks.checkArgument(serverTimeSql != null && !serverTimeSql.isBlank(), "serverTimeSql can not be blank!");
        this.serverTimeSql = serverTimeSql;
        checkStableColumn();
        checkLeaseColumns();
    }
    
    /**
     * Fail fast on a {@code cosid_machine} table without the {@code stable} column, instead of at the first claim.
     */
    private void checkStableColumn() {
        SQLException sqlException = probe(PROBE_STABLE_COLUMN);
        if (sqlException == null) {
            return;
        }
        if (probe(PROBE_TABLE) != null) {
            throw new IllegalStateException("Table cosid_machine does not exist: create it with JdbcMachineIdInitializer "
                + "(with JdbcDialect.getInitCosIdMachineTableSql() of your database outside MySQL).", sqlException);
        }
        throw new IllegalStateException("Table cosid_machine has no stable column, which JdbcMachineLeaseStore needs. Add it with: ["
                + ADD_STABLE_COLUMN_SQL + "] (MySQL) or [" + PostgreSqlDialect.ADD_STABLE_COLUMN_SQL + "] (PostgreSQL).",
            sqlException);
    }
    
    /**
     * Fail fast on a {@code cosid_machine} table created before 4.0, naming the statement that adds the lease columns.
     */
    private void checkLeaseColumns() {
        SQLException sqlException = probe(PROBE_LEASE_COLUMNS);
        if (sqlException == null) {
            return;
        }
        throw new IllegalStateException("Table cosid_machine has no lease columns (renew_time, watermark, lease_version), which "
                + "JdbcMachineLeaseStore needs since 4.0. Add them with: [" + ADD_LEASE_COLUMNS_SQL + "] (MySQL) or ["
                + PostgreSqlDialect.ADD_LEASE_COLUMNS_SQL + "] (PostgreSQL), or JdbcDialect.getAddLeaseColumnsSql() of your database. "
                + "3.4 instances keep working with them.",
            sqlException);
    }
    
    /**
     * Run a query that returns no row.
     *
     * @return {@code null} if it ran, or the SQLState class 42 error (syntax error or access rule violation: an unknown
     *     table or column) it failed with
     */
    private SQLException probe(String sql) {
        try {
            execute(connection -> {
                try (PreparedStatement statement = JdbcSupport.prepareStatement(connection, sql, queryTimeoutSeconds)) {
                    statement.executeQuery().close();
                }
                return null;
            });
            return null;
        } catch (CosIdException exception) {
            SQLException sqlException = (SQLException) exception.getCause();
            if (sqlException.getSQLState() == null || !sqlException.getSQLState().startsWith("42")) {
                throw exception;
            }
            return sqlException;
        }
    }
    
    @FunctionalInterface
    private interface ConnectionCallback<T> {
        T apply(Connection connection) throws SQLException;
    }
    
    private <T> T execute(ConnectionCallback<T> callback) {
        try (Connection connection = dataSource.getConnection()) {
            try {
                T result = callback.apply(connection);
                JdbcSupport.commitIfManual(connection);
                return result;
            } catch (SQLException | RuntimeException exception) {
                JdbcSupport.rollbackIfManual(connection, exception);
                throw exception;
            }
        } catch (SQLException sqlException) {
            throw new CosIdException(sqlException.getMessage(), sqlException);
        }
    }
    
    /**
     * The {@code last_timestamp} to write: strictly greater than the stored one, and at least the renewal time and the
     * watermark, so 3.4 instances never see a lease older, or a watermark lower, than it is.
     */
    private static long nextTimestamp(long stored, long renewedAt, long watermark) {
        return Math.max(stored + 1, Math.max(renewedAt, watermark));
    }
    
    private static int stableValue(InstanceId instanceId) {
        return instanceId.isStable() ? 1 : 0;
    }
    
    /**
     * A row read as a candidate, to be taken with a conditional update on its owner and last timestamp.
     */
    private static final class Row {
        private final int machineId;
        private final long lastTimestamp;
        private final String instanceId;
        private final long renewedAt;
        private final long watermark;
        
        private Row(int machineId, long lastTimestamp, String instanceId, long renewTime, long watermark, long leaseVersion) {
            this.machineId = machineId;
            this.lastTimestamp = lastTimestamp;
            this.instanceId = instanceId;
            boolean writtenByLeaseStore = leaseVersion == lastTimestamp;
            this.renewedAt = writtenByLeaseStore ? renewTime : lastTimestamp;
            this.watermark = writtenByLeaseStore ? watermark : lastTimestamp;
        }
    }
    
    private List<Row> select(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = JdbcSupport.prepareStatement(connection, sql, queryTimeoutSeconds)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            List<Row> rows = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new Row(resultSet.getInt(1), resultSet.getLong(2), resultSet.getString(3), resultSet.getLong(4),
                        resultSet.getLong(5), resultSet.getLong(6)));
                }
            }
            return rows;
        }
    }
    
    /**
     * Take a machine id: the new owner starts no earlier than the previous owner's watermark, last renewal and last
     * timestamp, and the store's current time.
     */
    private Optional<Lease> take(Connection connection, String namespace, InstanceId instanceId, Row row, long now) throws SQLException {
        long watermark = Math.max(Math.max(row.watermark, row.renewedAt), Math.max(row.lastTimestamp, now));
        long timestamp = nextTimestamp(row.lastTimestamp, now, watermark);
        try (PreparedStatement statement = JdbcSupport.prepareStatement(connection, TAKE, queryTimeoutSeconds)) {
            statement.setString(1, instanceId.getInstanceId());
            statement.setLong(2, timestamp);
            statement.setLong(3, now);
            statement.setInt(4, stableValue(instanceId));
            statement.setLong(5, now);
            statement.setLong(6, watermark);
            statement.setLong(7, timestamp);
            statement.setString(8, namespacedMachineId(namespace, row.machineId));
            statement.setString(9, row.instanceId);
            statement.setLong(10, row.lastTimestamp);
            if (statement.executeUpdate() == 0) {
                return Optional.empty();
            }
        }
        return Optional.of(new Lease(namespace, instanceId, row.machineId, watermark, now, timestamp));
    }
    
    /**
     * Take the first candidate whose conditional update applies. When another instance changed every candidate first,
     * read the candidates again, a bounded number of times.
     */
    private Optional<Lease> takeFirst(Connection connection, String namespace, InstanceId instanceId, long now, ConnectionCallback<List<Row>> candidates)
        throws SQLException {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            List<Row> rows = candidates.apply(connection);
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            for (Row row : rows) {
                Optional<Lease> lease = take(connection, namespace, instanceId, row, now);
                if (lease.isPresent()) {
                    return lease;
                }
            }
            // Nothing was written: without auto-commit, end the transaction so that the next read sees the other writes
            // (MySQL's repeatable read would otherwise return the same snapshot).
            if (!connection.getAutoCommit()) {
                connection.rollback();
            }
        }
        return Optional.empty();
    }
    
    @Override
    public Optional<Lease> claimSelf(String namespace, InstanceId instanceId, long now) {
        return execute(connection -> takeFirst(connection, namespace, instanceId, now,
            conn -> select(conn, SELECT_SELF, namespace, instanceId.getInstanceId())));
    }
    
    @Override
    public Optional<Lease> claimReleased(String namespace, InstanceId instanceId, int maxMachineId, long now) {
        return execute(connection -> takeFirst(connection, namespace, instanceId, now,
            conn -> select(conn, SELECT_RELEASED, namespace, maxMachineId)));
    }
    
    @Override
    public Optional<Lease> claimExpired(String namespace, InstanceId instanceId, int maxMachineId, long expiredBefore, long now) {
        return execute(connection -> takeFirst(connection, namespace, instanceId, now,
            conn -> select(conn, SELECT_EXPIRED, namespace, expiredBefore, maxMachineId)));
    }
    
    @Override
    public Optional<Lease> claimNew(String namespace, InstanceId instanceId, int maxMachineId, long now) {
        return execute(connection -> {
            for (int attempt = 1; ; attempt++) {
                int machineId = nextMachineId(connection, namespace);
                if (machineId > maxMachineId) {
                    return Optional.empty();
                }
                try (PreparedStatement statement = JdbcSupport.prepareStatement(connection, INSERT, queryTimeoutSeconds)) {
                    statement.setString(1, namespacedMachineId(namespace, machineId));
                    statement.setString(2, namespace);
                    statement.setInt(3, machineId);
                    statement.setLong(4, now);
                    statement.setString(5, instanceId.getInstanceId());
                    statement.setLong(6, now);
                    statement.setInt(7, stableValue(instanceId));
                    statement.setLong(8, now);
                    statement.setLong(9, now);
                    statement.setLong(10, now);
                    statement.executeUpdate();
                    return Optional.of(new Lease(namespace, instanceId, machineId, now, now, now));
                } catch (SQLException sqlException) {
                    // Another instance inserted the same machine id first.
                    if (!JdbcSupport.isDuplicateKey(sqlException) || attempt >= MAX_ATTEMPTS) {
                        throw sqlException;
                    }
                    // Nothing was written yet in this call; on PostgreSQL the failed insert also aborted the transaction.
                    JdbcSupport.rollbackIfManual(connection, sqlException);
                }
            }
        });
    }
    
    private int nextMachineId(Connection connection, String namespace) throws SQLException {
        try (PreparedStatement statement = JdbcSupport.prepareStatement(connection, SELECT_MAX_MACHINE_ID, queryTimeoutSeconds)) {
            statement.setString(1, namespace);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return 0;
                }
                int max = resultSet.getInt(1);
                return resultSet.wasNull() ? 0 : max + 1;
            }
        }
    }
    
    @Override
    public Optional<Lease> renew(Lease lease, long now) {
        long timestamp = nextTimestamp(lease.getVersion(), now, lease.getWatermark());
        boolean renewed = execute(connection -> {
            try (PreparedStatement statement = JdbcSupport.prepareStatement(connection, RENEW, queryTimeoutSeconds)) {
                statement.setLong(1, timestamp);
                statement.setInt(2, stableValue(lease.getInstanceId()));
                statement.setLong(3, now);
                statement.setLong(4, timestamp);
                statement.setString(5, namespacedMachineId(lease.getNamespace(), lease.getMachineId()));
                statement.setString(6, lease.getInstanceId().getInstanceId());
                statement.setLong(7, lease.getVersion());
                return statement.executeUpdate() > 0;
            }
        });
        if (!renewed) {
            return Optional.empty();
        }
        return Optional.of(new Lease(lease.getNamespace(), lease.getInstanceId(), lease.getMachineId(), lease.getWatermark(), now, timestamp));
    }
    
    @Override
    public boolean release(Lease lease, long watermark) {
        long newWatermark = Math.max(lease.getWatermark(), watermark);
        long timestamp = nextTimestamp(lease.getVersion(), lease.getRenewedAt(), newWatermark);
        boolean stable = lease.isStable();
        return execute(connection -> {
            try (PreparedStatement statement = JdbcSupport.prepareStatement(connection, RELEASE, queryTimeoutSeconds)) {
                statement.setString(1, stable ? lease.getInstanceId().getInstanceId() : "");
                statement.setLong(2, timestamp);
                statement.setLong(3, timestamp);
                statement.setInt(4, stable ? 1 : 0);
                statement.setLong(5, newWatermark);
                statement.setLong(6, timestamp);
                statement.setString(7, namespacedMachineId(lease.getNamespace(), lease.getMachineId()));
                statement.setString(8, lease.getInstanceId().getInstanceId());
                statement.setLong(9, lease.getVersion());
                return statement.executeUpdate() > 0;
            }
        });
    }
    
    /**
     * The database server's clock, read with {@code serverTimeSql}.
     */
    @Override
    public long serverTime() {
        return execute(connection -> {
            try (PreparedStatement statement = JdbcSupport.prepareStatement(connection, serverTimeSql, queryTimeoutSeconds);
                 ResultSet resultSet = statement.executeQuery()) {
                Checks.checkState(resultSet.next(), "serverTimeSql:[%s] returned no row!", serverTimeSql);
                return resultSet.getLong(1);
            }
        });
    }
}
