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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InMemoryMachineStateStorage;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;

/**
 * Cases of {@link JdbcMachineIdDistributor} beyond the shared spec, on a real MySQL.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcMachineIdDistributorMySqlTest {
    private static final String INSERT_MACHINE = "insert into cosid_machine";
    private static final InstanceId INSTANCE_ID = InstanceId.of("host", 1, false);
    
    DataSource realDataSource;
    RecordingDataSource dataSource;
    String namespace;
    
    @BeforeAll
    void setupDataSource() {
        realDataSource = RealMySql.createDataSource();
    }
    
    @BeforeEach
    void setup() {
        dataSource = new RecordingDataSource(realDataSource);
        namespace = MockIdGenerator.INSTANCE.generateAsString();
    }
    
    private record MachineRow(String instanceId, long lastTimestamp, boolean stable) {
    }
    
    private void putMachine(int machineId, String instanceId, long lastTimestamp) throws SQLException {
        try (Connection connection = realDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "insert into cosid_machine (name, namespace, machine_id, last_timestamp, instance_id, distribute_time, revert_time) "
                     + "values (?,?,?,?,?,0,0)")) {
            statement.setString(1, namespacedMachineId(namespace, machineId));
            statement.setString(2, namespace);
            statement.setInt(3, machineId);
            statement.setLong(4, lastTimestamp);
            statement.setString(5, instanceId);
            statement.executeUpdate();
        }
    }
    
    private Optional<MachineRow> findMachine(int machineId) throws SQLException {
        try (Connection connection = realDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "select instance_id, last_timestamp, stable from cosid_machine where name=?")) {
            statement.setString(1, namespacedMachineId(namespace, machineId));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new MachineRow(resultSet.getString(1), resultSet.getLong(2), resultSet.getInt(3) == 1));
            }
        }
    }
    
    private TestableJdbcMachineIdDistributor distributor() {
        return new TestableJdbcMachineIdDistributor(dataSource, JdbcSupport.DEFAULT_QUERY_TIMEOUT);
    }
    
    @Test
    void distributeShouldRecordStable() throws SQLException {
        MachineState actual = distributor().distributeRemoteForTest(namespace, 4, InstanceId.of("host", 2, true), Duration.ZERO);
        
        assertThat(findMachine(actual.getMachineId()).orElseThrow().stable(), equalTo(true));
    }
    
    @Test
    void distributeBySelfShouldClearStableWhenNoLongerStable() throws SQLException {
        TestableJdbcMachineIdDistributor distributor = distributor();
        int machineId = distributor.distributeRemoteForTest(namespace, 4, InstanceId.of("host", 2, true), Duration.ZERO).getMachineId();
        
        distributor.distributeRemoteForTest(namespace, 4, InstanceId.of("host", 2, false), Duration.ofMinutes(1));
        
        assertThat(findMachine(machineId).orElseThrow().stable(), equalTo(false));
    }
    
    @Test
    void distributeBySelfShouldReturnRemoteFutureTimestamp() throws SQLException {
        long futureLastTimestamp = System.currentTimeMillis() + Duration.ofMinutes(1).toMillis();
        putMachine(7, INSTANCE_ID.getInstanceId(), futureLastTimestamp);
        
        MachineState actual = distributor().distributeRemoteForTest(namespace, 4, INSTANCE_ID, Duration.ZERO);
        
        assertThat(actual.getMachineId(), equalTo(7));
        assertThat(actual.getLastTimeStamp(), equalTo(futureLastTimestamp));
        assertThat(findMachine(7).orElseThrow().lastTimestamp(), equalTo(futureLastTimestamp));
    }
    
    private void execute(String sql, Object... parameters) throws SQLException {
        try (Connection connection = realDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            statement.executeUpdate();
        }
    }
    
    @Test
    void distributeByRevertShouldTryNextCandidateWhenFirstUpdateLosesRace() throws SQLException {
        putMachine(1, "", 10);
        putMachine(2, "", 20);
        // Another live instance takes machine id 1 between this instance's read of the candidates and its update.
        dataSource.beforeNextExecution("update cosid_machine set instance_id=?,last_timestamp=?,distribute_time=?",
            () -> execute("update cosid_machine set instance_id='other', last_timestamp=? where name=?",
                System.currentTimeMillis() + Duration.ofMinutes(1).toMillis(), namespacedMachineId(namespace, 1)));
        
        MachineState actual = distributor().distributeRemoteForTest(namespace, 4, INSTANCE_ID, Duration.ZERO);
        
        assertThat(actual.getMachineId(), equalTo(2));
        assertThat(findMachine(1).orElseThrow().instanceId(), equalTo("other"));
        assertThat(findMachine(2).orElseThrow().instanceId(), equalTo(INSTANCE_ID.getInstanceId()));
    }
    
    @Test
    void distributeBySelfShouldFailWhenTheMachineIdIsRecycledInBetween() throws SQLException {
        putMachine(5, INSTANCE_ID.getInstanceId(), System.currentTimeMillis());
        dataSource.beforeNextExecution("update cosid_machine set last_timestamp=?,stable=?",
            () -> execute("update cosid_machine set instance_id='other' where name=?", namespacedMachineId(namespace, 5)));
        
        Assertions.assertThrows(MachineIdLostException.class,
            () -> distributor().distributeRemoteForTest(namespace, 4, INSTANCE_ID, Duration.ofMinutes(1)));
    }
    
    /**
     * A table created before the stable column existed: distribution keeps working, and a stable instance gets its own
     * machine id back. The legacy table is a temporary table on one shared connection, which hides the real one.
     */
    @Test
    void tableWithoutStableColumnKeepsDistributing() throws SQLException {
        try (Connection shared = realDataSource.getConnection()) {
            try (PreparedStatement statement = shared.prepareStatement(JdbcMachineIdInitializer.INIT_COSID_MACHINE_TABLE_SQL
                .replace("create table if not exists cosid_machine", "create temporary table cosid_machine")
                .replace("    stable          tinyint          not null default 0,\n", ""))) {
                statement.execute();
            }
            Connection unclosable = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> "close".equals(method.getName()) ? null : method.invoke(shared, args));
            DataSource legacy = (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class},
                (proxy, method, args) -> "getConnection".equals(method.getName()) ? unclosable : method.invoke(realDataSource, args));
            InstanceId stableInstance = InstanceId.of("host", 2, true);
            
            int machineId = new TestableJdbcMachineIdDistributor(legacy, JdbcSupport.DEFAULT_QUERY_TIMEOUT)
                .distributeRemoteForTest(namespace, 4, stableInstance, Duration.ZERO).getMachineId();
            int again = new TestableJdbcMachineIdDistributor(legacy, JdbcSupport.DEFAULT_QUERY_TIMEOUT)
                .distributeRemoteForTest(namespace, 4, stableInstance, Duration.ZERO).getMachineId();
            
            assertThat(again, equalTo(machineId));
            assertThat(findMachine(machineId).isPresent(), equalTo(false));
        }
    }
    
    @Test
    void revertRemoteShouldReleaseUnstableInstanceForReuse() throws SQLException {
        putMachine(3, INSTANCE_ID.getInstanceId(), 1);
        
        distributor().revertRemoteForTest(namespace, INSTANCE_ID, MachineState.of(3, 123));
        
        MachineRow actual = findMachine(3).orElseThrow();
        assertThat(actual.instanceId(), equalTo(""));
        assertThat(actual.lastTimestamp(), equalTo(123L));
    }
    
    @Test
    void guardRemoteShouldUpdateRemoteTimestamp() throws SQLException {
        putMachine(4, INSTANCE_ID.getInstanceId(), 1);
        
        distributor().guardRemoteForTest(namespace, INSTANCE_ID, MachineState.of(4, 456), Duration.ZERO);
        
        assertThat(findMachine(4).orElseThrow().lastTimestamp(), equalTo(456L));
    }
    
    @Test
    void distributeMachineShouldRetryWhenDuplicateIsReportedBySqlStateClass23() throws SQLException {
        dataSource.failExecutions(INSERT_MACHINE, "23505", 0, 1);
        
        MachineState actual = distributor().distributeRemoteForTest(namespace, 4, INSTANCE_ID, Duration.ZERO);
        
        assertThat(actual.getMachineId(), equalTo(0));
        assertThat(findMachine(0).orElseThrow().instanceId(), equalTo(INSTANCE_ID.getInstanceId()));
    }
    
    @Test
    void distributeMachineShouldNotRetryOnOtherSqlState() throws SQLException {
        dataSource.failExecutions(INSERT_MACHINE, "08006", 0, 1);
        
        CosIdException actual = Assertions.assertThrows(CosIdException.class,
            () -> distributor().distributeRemoteForTest(namespace, 4, INSTANCE_ID, Duration.ZERO));
        
        assertThat(((SQLException) actual.getCause()).getSQLState(), equalTo("08006"));
        assertThat(findMachine(0).isPresent(), equalTo(false));
    }
    
    @Test
    void distributeMachineShouldNotRetryOnNonDuplicateIntegrityViolation() {
        // MySQL reports NOT NULL violations as 23000 too; only 1062 is a duplicate key.
        dataSource.failExecutions(INSERT_MACHINE, "23000", 1048, 1);
        
        Assertions.assertThrows(CosIdException.class, () -> distributor().distributeRemoteForTest(namespace, 4, INSTANCE_ID, Duration.ZERO));
        
        assertThat(dataSource.getAttempts(), equalTo(1));
    }
    
    @Test
    void distributeMachineShouldRetryMySqlDuplicate() {
        dataSource.failExecutions(INSERT_MACHINE, "23000", 1062, 3);
        
        MachineState actual = distributor().distributeRemoteForTest(namespace, 4, INSTANCE_ID, Duration.ZERO);
        
        assertThat(actual.getMachineId(), equalTo(0));
        assertThat(dataSource.getAttempts(), equalTo(4));
    }
    
    @Test
    void distributeMachineShouldStopRetryingAfterMaxAttempts() {
        dataSource.failExecutions(INSERT_MACHINE, "23505", 0, Integer.MAX_VALUE);
        
        CosIdException actual = Assertions.assertThrows(CosIdException.class,
            () -> distributor().distributeRemoteForTest(namespace, 4, INSTANCE_ID, Duration.ZERO));
        
        assertThat(((SQLException) actual.getCause()).getSQLState(), equalTo("23505"));
        assertThat(dataSource.getAttempts(), equalTo(JdbcMachineIdDistributor.MAX_DISTRIBUTE_ATTEMPTS));
    }
    
    @Test
    void statementsShouldUseQueryTimeoutRoundedUpToSeconds() {
        new JdbcMachineIdDistributor(dataSource, new InMemoryMachineStateStorage(), ClockBackwardsSynchronizer.DEFAULT, Duration.ofMillis(1500))
            .distribute(namespace, 4, INSTANCE_ID, Duration.ZERO);
        
        assertThat(dataSource.getQueryTimeouts(), not(empty()));
        assertThat(dataSource.getQueryTimeouts(), everyItem(equalTo(2)));
    }
    
    @Test
    void statementsShouldUseDefaultQueryTimeout() {
        new JdbcMachineIdDistributor(dataSource, new InMemoryMachineStateStorage(), ClockBackwardsSynchronizer.DEFAULT)
            .distribute(namespace, 4, INSTANCE_ID, Duration.ZERO);
        
        assertThat(dataSource.getQueryTimeouts(), not(empty()));
        assertThat(dataSource.getQueryTimeouts(), everyItem(equalTo(5)));
    }
    
    @Test
    void zeroQueryTimeoutShouldNotSetTimeout() {
        new JdbcMachineIdDistributor(dataSource, new InMemoryMachineStateStorage(), ClockBackwardsSynchronizer.DEFAULT, Duration.ZERO)
            .distribute(namespace, 4, INSTANCE_ID, Duration.ZERO);
        
        assertThat(dataSource.getQueryTimeouts(), empty());
    }
    
    private static final class TestableJdbcMachineIdDistributor extends JdbcMachineIdDistributor {
        private TestableJdbcMachineIdDistributor(DataSource dataSource, Duration queryTimeout) {
            super(dataSource, new InMemoryMachineStateStorage(), ClockBackwardsSynchronizer.DEFAULT, queryTimeout);
        }
        
        private MachineState distributeRemoteForTest(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
            return super.distributeRemote(namespace, machineBit, instanceId, safeGuardDuration);
        }
        
        private void revertRemoteForTest(String namespace, InstanceId instanceId, MachineState machineState) {
            super.revertRemote(namespace, instanceId, machineState);
        }
        
        private void guardRemoteForTest(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) {
            super.guardRemote(namespace, instanceId, machineState, safeGuardDuration);
        }
    }
}
