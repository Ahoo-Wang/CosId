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

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.machine.AbstractMachineIdDistributor;
import me.ahoo.cosid.snowflake.machine.MachineIdDistributor;
import me.ahoo.cosid.snowflake.machine.MachineIdLostException;
import me.ahoo.cosid.snowflake.machine.InstanceId;
import me.ahoo.cosid.snowflake.machine.MachineIdOverflowException;
import me.ahoo.cosid.snowflake.machine.MachineState;
import me.ahoo.cosid.snowflake.machine.MachineStateStorage;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Duration;

/**
 * Jdbc MachineId Distributor.
 *
 * @author ahoo wang
 */
@Slf4j
public class JdbcMachineIdDistributor extends AbstractMachineIdDistributor {
    
    private final DataSource dataSource;
    
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
    
    public JdbcMachineIdDistributor(DataSource dataSource, MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        super(machineStateStorage, clockBackwardsSynchronizer, FOREVER_SAFE_GUARD_DURATION);
        this.dataSource = dataSource;
    }
    
    public JdbcMachineIdDistributor(DataSource dataSource, MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer, Duration safeGuardDuration) {
        super(machineStateStorage, clockBackwardsSynchronizer, safeGuardDuration);
        this.dataSource = dataSource;
    }
    
    private String getNamespacedMachineId(String namespace, int machineId) {
        return namespace + "." + Strings.padStart(String.valueOf(machineId), 4, '0');
    }
    
    private int distributeRevertMachineState(Connection connection, String namespace, int machineId, InstanceId instanceId) throws SQLException {
        try (PreparedStatement revertMachineStatement = connection.prepareStatement(DISTRIBUTE_REVERT_MACHINE_STATE)) {
            revertMachineStatement.setString(1, instanceId.getInstanceId());
            revertMachineStatement.setLong(2, System.currentTimeMillis());
            revertMachineStatement.setLong(3, System.currentTimeMillis());
            revertMachineStatement.setString(4, getNamespacedMachineId(namespace, machineId));
            revertMachineStatement.setLong(5, getSafeGuardAt(instanceId.isStable()));
            int affected = revertMachineStatement.executeUpdate();
            return affected;
        }
    }
    
    private int nextMachineId(Connection connection, String namespace) throws SQLException {
        try (PreparedStatement nextMachineStatement = connection.prepareStatement(NEXT_MACHINE_ID)) {
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
    protected MachineState distributeRemote(String namespace, int machineBit, InstanceId instanceId) {
        if (log.isInfoEnabled()) {
            log.info("distributeRemote - instanceId:[{}] - machineBit:[{}] @ namespace:[{}].", instanceId, machineBit, namespace);
        }
        try (Connection connection = dataSource.getConnection()) {
            MachineState machineState = distributeBySelf(namespace, instanceId, connection);
            if (machineState != null) {
                return machineState;
            }
            
            machineState = distributeByRevert(namespace, instanceId, connection);
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
        int nextMachineId = nextMachineId(connection, namespace);
        if (nextMachineId > MachineIdDistributor.maxMachineId(machineBit)) {
            throw new MachineIdOverflowException(MachineIdDistributor.totalMachineIds(machineBit), instanceId);
        }
        MachineState nextMachineState = MachineState.of(nextMachineId, System.currentTimeMillis());
        try (PreparedStatement nextMachineStatement = connection.prepareStatement(DISTRIBUTE_MACHINE)) {
            nextMachineStatement.setString(1, getNamespacedMachineId(namespace, nextMachineId));
            nextMachineStatement.setString(2, namespace);
            nextMachineStatement.setInt(3, nextMachineId);
            nextMachineStatement.setLong(4, nextMachineState.getLastTimeStamp());
            nextMachineStatement.setString(5, instanceId.getInstanceId());
            nextMachineStatement.setLong(6, System.currentTimeMillis());
            try {
                nextMachineStatement.executeUpdate();
                return nextMachineState;
            } catch (SQLIntegrityConstraintViolationException sqlIntegrityConstraintViolationException) {
                if (log.isInfoEnabled()) {
                    log.info("distributeMachine - [{}]", sqlIntegrityConstraintViolationException.getMessage());
                }
                return distributeMachine(namespace, machineBit, instanceId, connection);
            }
        }
    }
    
    private MachineState distributeByRevert(String namespace, InstanceId instanceId, Connection connection) throws SQLException {
        try (PreparedStatement getRevertMachineStatement = connection.prepareStatement(GET_REVERT_MACHINE_STATE)) {
            getRevertMachineStatement.setString(1, namespace);
            getRevertMachineStatement.setLong(2, getSafeGuardAt(instanceId.isStable()));
            try (ResultSet resultSet = getRevertMachineStatement.executeQuery()) {
                if (resultSet.next()) {
                    int machineId = resultSet.getInt(1);
                    long lastTimeStamp = resultSet.getLong(2);
                    if (distributeRevertMachineState(connection, namespace, machineId, instanceId) > 0) {
                        return MachineState.of(machineId, lastTimeStamp);
                    }
                }
            }
        }
        return null;
    }
    
    private MachineState distributeBySelf(String namespace, InstanceId instanceId, Connection connection) throws SQLException {
        try (PreparedStatement getMachineStatement = connection.prepareStatement(GET_MACHINE_STATE)) {
            getMachineStatement.setString(1, namespace);
            getMachineStatement.setString(2, instanceId.getInstanceId());
            getMachineStatement.setLong(3, getSafeGuardAt(instanceId.isStable()));
            try (ResultSet resultSet = getMachineStatement.executeQuery()) {
                if (resultSet.next()) {
                    int machineId = resultSet.getInt(1);
                    MachineState machineState = MachineState.of(machineId, System.currentTimeMillis());
                    guardRemote(namespace, instanceId, machineState);
                    return machineState;
                }
            }
        }
        return null;
    }
    
    @Override
    protected void revertRemote(String namespace, InstanceId instanceId, MachineState machineState) {
        if (log.isInfoEnabled()) {
            log.info("revertRemote - [{}] instanceId:[{}] @ namespace:[{}].", machineState, instanceId, namespace);
        }
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement revertMachineStatement = connection.prepareStatement(REVERT_MACHINE_STATE)) {
                revertMachineStatement.setString(1, instanceId.isStable() ? instanceId.getInstanceId() : "");
                revertMachineStatement.setLong(2, machineState.getLastTimeStamp());
                revertMachineStatement.setLong(3, System.currentTimeMillis());
                revertMachineStatement.setString(4, namespace);
                revertMachineStatement.setString(5, instanceId.getInstanceId());
                int affected = revertMachineStatement.executeUpdate();
                if (log.isInfoEnabled()) {
                    log.info("revertRemote - affected:[{}]", affected);
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
    protected void guardRemote(String namespace, InstanceId instanceId, MachineState machineState) {
        if (log.isInfoEnabled()) {
            log.info("guardRemote - [{}] instanceId:[{}] @ namespace:[{}].", machineState, instanceId, namespace);
        }
        
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement guardMachineStatement = connection.prepareStatement(GUARD_MACHINE_STATE)) {
                guardMachineStatement.setLong(1, machineState.getLastTimeStamp());
                guardMachineStatement.setString(2, namespace);
                guardMachineStatement.setString(3, instanceId.getInstanceId());
                guardMachineStatement.setInt(4, machineState.getMachineId());
                int affected = guardMachineStatement.executeUpdate();
                if (log.isInfoEnabled()) {
                    log.info("guardRemote - affected:[{}]", affected);
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
