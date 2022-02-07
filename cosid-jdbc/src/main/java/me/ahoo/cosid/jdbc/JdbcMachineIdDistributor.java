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

/**
 * Jdbc MachineId Distributor.
 *
 * @author ahoo wang
 */
@Slf4j
public class JdbcMachineIdDistributor extends AbstractMachineIdDistributor {

    private final DataSource dataSource;

    private static final String GET_MACHINE_STATE =
        "select machine_id, last_timestamp from cosid_machine where namespace=? and instance_id=?";

    private static final String GET_REVERT_MACHINE_STATE =
        "select machine_id, last_timestamp from cosid_machine where namespace=? and instance_id =''";

    private static final String DISTRIBUTE_REVERT_MACHINE_STATE =
        "update cosid_machine set instance_id=?,distribute_time=? where name=? and instance_id=''";

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

    public JdbcMachineIdDistributor(DataSource dataSource, MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        super(machineStateStorage, clockBackwardsSynchronizer);
        this.dataSource = dataSource;
    }

    private String getNamespacedMachineId(String namespace, int machineId) {
        return namespace + "." + Strings.padStart(String.valueOf(machineId), 4, '0');
    }

    private int distributeRevertMachineState(Connection connection, String namespace, int machineId, InstanceId instanceId) throws SQLException {
        try (PreparedStatement revertMachineStatement = connection.prepareStatement(DISTRIBUTE_REVERT_MACHINE_STATE)) {
            revertMachineStatement.setString(1, instanceId.getInstanceId());
            revertMachineStatement.setLong(2, System.currentTimeMillis());
            revertMachineStatement.setString(3, getNamespacedMachineId(namespace, machineId));
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
    protected MachineState distribute0(String namespace, int machineBit, InstanceId instanceId) {
        if (log.isInfoEnabled()) {
            log.info("distribute0 - instanceId:[{}] - machineBit:[{}] @ namespace:[{}].", instanceId, machineBit, namespace);
        }
        try (Connection connection = dataSource.getConnection()) {

            MachineState machineState = getMachineStateBySelf(namespace, instanceId, connection);
            if (machineState != null) {
                return machineState;
            }

            machineState = getMachineStateByRevert(namespace, instanceId, connection);
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
        if (nextMachineId > maxMachineId(machineBit)) {
            throw new MachineIdOverflowException(totalMachineIds(machineBit), instanceId);
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

    private MachineState getMachineStateByRevert(String namespace, InstanceId instanceId, Connection connection) throws SQLException {
        try (PreparedStatement getRevertMachineStatement = connection.prepareStatement(GET_REVERT_MACHINE_STATE)) {
            getRevertMachineStatement.setString(1, namespace);
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

    private MachineState getMachineStateBySelf(String namespace, InstanceId instanceId, Connection connection) throws SQLException {
        try (PreparedStatement getMachineStatement = connection.prepareStatement(GET_MACHINE_STATE)) {
            getMachineStatement.setString(1, namespace);
            getMachineStatement.setString(2, instanceId.getInstanceId());
            try (ResultSet resultSet = getMachineStatement.executeQuery()) {
                if (resultSet.next()) {
                    int machineId = resultSet.getInt(1);
                    long lastTimeStamp = resultSet.getLong(2);
                    return MachineState.of(machineId, lastTimeStamp);
                }
            }
        }
        return null;
    }

    @Override
    protected void revert0(String namespace, InstanceId instanceId, MachineState machineState) {
        if (log.isInfoEnabled()) {
            log.info("revert0 - [{}] instanceId:[{}] @ namespace:[{}].", machineState, instanceId, namespace);
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
                    log.info("revert0 - affected:[{}]", affected);
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
