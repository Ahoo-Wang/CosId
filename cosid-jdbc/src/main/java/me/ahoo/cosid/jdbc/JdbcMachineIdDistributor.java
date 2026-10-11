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
import me.ahoo.cosid.machine.AbstractMachineIdDistributor;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineIdOverflowException;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.MachineStateStorage;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;

/**
 * Jdbc MachineId Distributor.
 *
 * @author ahoo wang
 */
@Slf4j
public class JdbcMachineIdDistributor extends AbstractMachineIdDistributor {
    
    private final DataSource dataSource;
    private final int queryTimeoutSeconds;
    
    /**
     * Upper bound of insert attempts when concurrent instances race for the same new machine id.
     */
    static final int MAX_DISTRIBUTE_ATTEMPTS = 32;
    
    private static final String GET_MACHINE_STATE =
        "select machine_id, last_timestamp from cosid_machine where namespace=? and instance_id=? and last_timestamp>?";
    
    private static final String GET_REVERT_MACHINE_STATE =
        "select machine_id, last_timestamp from cosid_machine where namespace=? and (instance_id='' or last_timestamp<=?)";
    
    private static final String DISTRIBUTE_REVERT_MACHINE_STATE =
        "update cosid_machine "
            + "set instance_id=?,last_timestamp=?,distribute_time=? "
            + "where name=? and (instance_id='' or last_timestamp<=?)";
    
    private static final String NEXT_MACHINE_ID =
        "select max(machine_id)+1 as next_machine_id from cosid_machine where namespace=?";
    
    private static final String DISTRIBUTE_MACHINE =
        "insert into cosid_machine "
            + "(name, namespace, machine_id, last_timestamp, instance_id, distribute_time, revert_time) "
            + "values "
            + "(?,?,?,?,?,?,0);";
    
    private static final String REVERT_MACHINE_STATE =
        "update cosid_machine "
            + "set instance_id=?,last_timestamp=?,revert_time=? "
            + "where namespace=? and instance_id=?";
    
    private static final String GUARD_MACHINE_STATE =
        "update cosid_machine "
            + "set last_timestamp=? "
            + "where namespace=? and instance_id=? and machine_id=?";
    
    /*
     * With the stable column: the machine id of a stable instance is never recycled by other instances.
     */
    
    /**
     * Fails on tables created before the {@code stable} column existed.
     */
    private static final String PROBE_STABLE_COLUMN = "select stable from cosid_machine where 1=0";
    
    /**
     * Migration of a table created before the {@code stable} column existed (MySQL syntax).
     */
    public static final String ADD_STABLE_COLUMN_SQL = "alter table cosid_machine add column stable tinyint not null default 0";
    
    private static final String GET_REVERT_MACHINE_STATE_EXCLUDING_STABLE =
        "select machine_id, last_timestamp from cosid_machine where namespace=? and (instance_id='' or (last_timestamp<=? and stable=0))";
    
    private static final String DISTRIBUTE_REVERT_MACHINE_STATE_WITH_STABLE =
        "update cosid_machine "
            + "set instance_id=?,last_timestamp=?,distribute_time=?,stable=? "
            + "where name=? and (instance_id='' or (last_timestamp<=? and stable=0))";
    
    private static final String DISTRIBUTE_MACHINE_WITH_STABLE =
        "insert into cosid_machine "
            + "(name, namespace, machine_id, last_timestamp, instance_id, distribute_time, revert_time, stable) "
            + "values "
            + "(?,?,?,?,?,?,0,?);";
    
    private static final String DISTRIBUTE_SELF_WITH_STABLE =
        "update cosid_machine "
            + "set last_timestamp=?,stable=? "
            + "where namespace=? and instance_id=? and machine_id=?";
    
    /**
     * Whether {@code cosid_machine} has the {@code stable} column, probed once.
     */
    private volatile Boolean stableColumn;
    
    public JdbcMachineIdDistributor(DataSource dataSource, MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        this(dataSource, machineStateStorage, clockBackwardsSynchronizer, JdbcSupport.DEFAULT_QUERY_TIMEOUT);
    }
    
    /**
     * Create a JDBC machine id distributor.
     *
     * @param queryTimeout timeout applied to each statement, {@code null} or zero means no timeout
     */
    public JdbcMachineIdDistributor(DataSource dataSource, MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer,
                                    Duration queryTimeout) {
        super(machineStateStorage, clockBackwardsSynchronizer);
        this.dataSource = dataSource;
        this.queryTimeoutSeconds = JdbcSupport.toQueryTimeoutSeconds(queryTimeout);
    }
    
    private int distributeRevertMachineState(Connection connection, String namespace, int machineId, InstanceId instanceId, Duration safeGuardDuration) throws SQLException {
        if (hasStableColumn(connection)) {
            try (PreparedStatement revertMachineStatement = JdbcSupport.prepareStatement(connection, DISTRIBUTE_REVERT_MACHINE_STATE_WITH_STABLE, queryTimeoutSeconds)) {
                revertMachineStatement.setString(1, instanceId.getInstanceId());
                revertMachineStatement.setLong(2, System.currentTimeMillis());
                revertMachineStatement.setLong(3, System.currentTimeMillis());
                revertMachineStatement.setInt(4, stableValue(instanceId));
                revertMachineStatement.setString(5, namespacedMachineId(namespace, machineId));
                revertMachineStatement.setLong(6, MachineIdDistributor.getSafeGuardAt(safeGuardDuration, instanceId.isStable()));
                return revertMachineStatement.executeUpdate();
            }
        }
        try (PreparedStatement revertMachineStatement = JdbcSupport.prepareStatement(connection, DISTRIBUTE_REVERT_MACHINE_STATE, queryTimeoutSeconds)) {
            revertMachineStatement.setString(1, instanceId.getInstanceId());
            revertMachineStatement.setLong(2, System.currentTimeMillis());
            revertMachineStatement.setLong(3, System.currentTimeMillis());
            revertMachineStatement.setString(4, namespacedMachineId(namespace, machineId));
            revertMachineStatement.setLong(5, MachineIdDistributor.getSafeGuardAt(safeGuardDuration, instanceId.isStable()));
            int affected = revertMachineStatement.executeUpdate();
            return affected;
        }
    }
    
    private int nextMachineId(Connection connection, String namespace) throws SQLException {
        try (PreparedStatement nextMachineStatement = JdbcSupport.prepareStatement(connection, NEXT_MACHINE_ID, queryTimeoutSeconds)) {
            nextMachineStatement.setString(1, namespace);
            try (ResultSet resultSet = nextMachineStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
                return 0;
            }
        }
    }
    
    @Override
    protected MachineState distributeRemote(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
        if (log.isInfoEnabled()) {
            log.info("Distribute Remote instanceId:[{}] - machineBit:[{}] @ namespace:[{}].", instanceId, machineBit, namespace);
        }
        try (Connection connection = dataSource.getConnection()) {
            MachineState machineState = distributeBySelf(namespace, instanceId, connection, safeGuardDuration);
            if (machineState != null) {
                return machineState;
            }
            
            machineState = distributeByRevert(namespace, instanceId, connection, safeGuardDuration);
            if (machineState != null) {
                return machineState;
            }
            
            return distributeMachine(namespace, machineBit, instanceId, connection);
        } catch (SQLException sqlException) {
            if (log.isErrorEnabled()) {
                log.error(sqlException.getMessage(), sqlException);
            }
            throw new CosIdException(sqlException.getMessage(), sqlException);
        }
    }
    
    private MachineState distributeMachine(String namespace, int machineBit, InstanceId instanceId, Connection connection) throws SQLException {
        return distributeMachine(namespace, machineBit, instanceId, connection, 1);
    }
    
    private MachineState distributeMachine(String namespace, int machineBit, InstanceId instanceId, Connection connection, int attempt) throws SQLException {
        int nextMachineId = nextMachineId(connection, namespace);
        if (nextMachineId > MachineIdDistributor.maxMachineId(machineBit)) {
            throw new MachineIdOverflowException(MachineIdDistributor.totalMachineIds(machineBit), instanceId);
        }
        MachineState nextMachineState = MachineState.of(nextMachineId, System.currentTimeMillis());
        boolean stableColumnPresent = hasStableColumn(connection);
        String distributeMachineSql = stableColumnPresent ? DISTRIBUTE_MACHINE_WITH_STABLE : DISTRIBUTE_MACHINE;
        try (PreparedStatement nextMachineStatement = JdbcSupport.prepareStatement(connection, distributeMachineSql, queryTimeoutSeconds)) {
            nextMachineStatement.setString(1, namespacedMachineId(namespace, nextMachineId));
            nextMachineStatement.setString(2, namespace);
            nextMachineStatement.setInt(3, nextMachineId);
            nextMachineStatement.setLong(4, nextMachineState.getLastTimeStamp());
            nextMachineStatement.setString(5, instanceId.getInstanceId());
            nextMachineStatement.setLong(6, System.currentTimeMillis());
            if (stableColumnPresent) {
                nextMachineStatement.setInt(7, stableValue(instanceId));
            }
            try {
                nextMachineStatement.executeUpdate();
                return nextMachineState;
            } catch (SQLException sqlException) {
                // Another instance inserted the same machine id first: recompute and retry, a bounded number of times.
                if (!JdbcSupport.isDuplicateKey(sqlException) || attempt >= MAX_DISTRIBUTE_ATTEMPTS) {
                    throw sqlException;
                }
                if (log.isInfoEnabled()) {
                    log.info("Distribute Machine attempt:[{}] - [{}]", attempt, sqlException.getMessage());
                }
                return distributeMachine(namespace, machineBit, instanceId, connection, attempt + 1);
            }
        }
    }
    
    private MachineState distributeByRevert(String namespace, InstanceId instanceId, Connection connection, Duration safeGuardDuration) throws SQLException {
        String getRevertMachineStateSql = hasStableColumn(connection) ? GET_REVERT_MACHINE_STATE_EXCLUDING_STABLE : GET_REVERT_MACHINE_STATE;
        try (PreparedStatement getRevertMachineStatement = JdbcSupport.prepareStatement(connection, getRevertMachineStateSql, queryTimeoutSeconds)) {
            getRevertMachineStatement.setString(1, namespace);
            getRevertMachineStatement.setLong(2, MachineIdDistributor.getSafeGuardAt(safeGuardDuration, instanceId.isStable()));
            try (ResultSet resultSet = getRevertMachineStatement.executeQuery()) {
                while (resultSet.next()) {
                    int machineId = resultSet.getInt(1);
                    long lastTimeStamp = resultSet.getLong(2);
                    if (distributeRevertMachineState(connection, namespace, machineId, instanceId, safeGuardDuration) > 0) {
                        return MachineState.of(machineId, lastTimeStamp);
                    }
                }
            }
        }
        return null;
    }
    
    private MachineState distributeBySelf(String namespace, InstanceId instanceId, Connection connection, Duration safeGuardDuration) throws SQLException {
        try (PreparedStatement getMachineStatement = JdbcSupport.prepareStatement(connection, GET_MACHINE_STATE, queryTimeoutSeconds)) {
            getMachineStatement.setString(1, namespace);
            getMachineStatement.setString(2, instanceId.getInstanceId());
            getMachineStatement.setLong(3, MachineIdDistributor.getSafeGuardAt(safeGuardDuration, instanceId.isStable()));
            try (ResultSet resultSet = getMachineStatement.executeQuery()) {
                if (resultSet.next()) {
                    int machineId = resultSet.getInt(1);
                    long lastTimeStamp = resultSet.getLong(2);
                    MachineState machineState = MachineState.of(machineId, Math.max(lastTimeStamp, System.currentTimeMillis()));
                    if (hasStableColumn(connection)) {
                        // Also records a change of the stable setting of this instance.
                        distributeSelfWithStable(connection, namespace, instanceId, machineState);
                    } else {
                        guardRemote(namespace, instanceId, machineState, safeGuardDuration);
                    }
                    return machineState;
                }
            }
        }
        return null;
    }
    
    private void distributeSelfWithStable(Connection connection, String namespace, InstanceId instanceId, MachineState machineState) throws SQLException {
        try (PreparedStatement selfStatement = JdbcSupport.prepareStatement(connection, DISTRIBUTE_SELF_WITH_STABLE, queryTimeoutSeconds)) {
            selfStatement.setLong(1, machineState.getLastTimeStamp());
            selfStatement.setInt(2, stableValue(instanceId));
            selfStatement.setString(3, namespace);
            selfStatement.setString(4, instanceId.getInstanceId());
            selfStatement.setInt(5, machineState.getMachineId());
            if (selfStatement.executeUpdate() == 0) {
                throw new MachineIdLostException(namespace, instanceId, machineState);
            }
        }
    }
    
    private static int stableValue(InstanceId instanceId) {
        return instanceId.isStable() ? 1 : 0;
    }
    
    /**
     * Probe once whether {@code cosid_machine} has the {@code stable} column. Without it (a table created by an older
     * version), the machine id of a stable instance can still be recycled after it has been offline for longer than the
     * safe guard duration, as before; add the column with {@link #ADD_STABLE_COLUMN_SQL} to prevent that.
     */
    private boolean hasStableColumn(Connection connection) throws SQLException {
        Boolean present = stableColumn;
        if (present != null) {
            return present;
        }
        try (PreparedStatement probeStatement = JdbcSupport.prepareStatement(connection, PROBE_STABLE_COLUMN, queryTimeoutSeconds)) {
            probeStatement.executeQuery().close();
            present = true;
        } catch (SQLException sqlException) {
            // SQLState class 42: syntax error or access rule violation, here an unknown column. Anything else is not an answer.
            if (sqlException.getSQLState() == null || !sqlException.getSQLState().startsWith("42")) {
                throw sqlException;
            }
            present = false;
            if (log.isWarnEnabled()) {
                log.warn("Table cosid_machine has no stable column, so the machine id of a stable instance may be recycled by other instances "
                    + "once it has been offline for longer than the safe guard duration. Add it with: [{}].", ADD_STABLE_COLUMN_SQL);
            }
        }
        stableColumn = present;
        return present;
    }
    
    @Override
    protected void revertRemote(String namespace, InstanceId instanceId, MachineState machineState) {
        if (log.isInfoEnabled()) {
            log.info("Revert Remote [{}] instanceId:[{}] @ namespace:[{}].", machineState, instanceId, namespace);
        }
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement revertMachineStatement = JdbcSupport.prepareStatement(connection, REVERT_MACHINE_STATE, queryTimeoutSeconds)) {
                revertMachineStatement.setString(1, instanceId.isStable() ? instanceId.getInstanceId() : "");
                revertMachineStatement.setLong(2, machineState.getLastTimeStamp());
                revertMachineStatement.setLong(3, System.currentTimeMillis());
                revertMachineStatement.setString(4, namespace);
                revertMachineStatement.setString(5, instanceId.getInstanceId());
                int affected = revertMachineStatement.executeUpdate();
                if (log.isInfoEnabled()) {
                    log.info("Revert Remote affected:[{}]", affected);
                }
            }
        } catch (SQLException sqlException) {
            if (log.isErrorEnabled()) {
                log.error(sqlException.getMessage(), sqlException);
            }
            throw new CosIdException(sqlException.getMessage(), sqlException);
        }
    }
    
    @Override
    protected void guardRemote(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) {
        if (log.isDebugEnabled()) {
            log.debug("Guard Remote - [{}] instanceId:[{}] @ namespace:[{}].", machineState, instanceId, namespace);
        }
        
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement guardMachineStatement = JdbcSupport.prepareStatement(connection, GUARD_MACHINE_STATE, queryTimeoutSeconds)) {
                guardMachineStatement.setLong(1, machineState.getLastTimeStamp());
                guardMachineStatement.setString(2, namespace);
                guardMachineStatement.setString(3, instanceId.getInstanceId());
                guardMachineStatement.setInt(4, machineState.getMachineId());
                int affected = guardMachineStatement.executeUpdate();
                if (log.isDebugEnabled()) {
                    log.debug("Guard Remote - affected:[{}]", affected);
                }
                
                if (0 == affected) {
                    throw new MachineIdLostException(namespace, instanceId, machineState);
                }
            }
        } catch (SQLException sqlException) {
            if (log.isErrorEnabled()) {
                log.error(sqlException.getMessage(), sqlException);
            }
            throw new CosIdException(sqlException.getMessage(), sqlException);
        }
    }
}
