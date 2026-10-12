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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InMemoryMachineStateStorage;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;

/**
 * With a pool configured with {@code autoCommit=false}, every write must be committed: it must be visible from another
 * connection afterwards.
 */
abstract class ManualCommitSpec {
    
    /**
     * Auto-commit data source used to check what was committed.
     */
    protected abstract DataSource dataSource();
    
    protected abstract JdbcIdSegmentDistributorFactory segmentDistributorFactory(DataSource manualCommitDataSource);
    
    protected abstract JdbcMachineLeaseStore machineLeaseStore(DataSource manualCommitDataSource);
    
    private static long queryLong(DataSource dataSource, String sql, String... parameters) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setString(i + 1, parameters[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : -1;
            }
        }
    }
    
    @Test
    void segmentInitializationAndNextMaxIdAreCommitted() throws SQLException {
        String name = MockIdGenerator.usePrefix("manual").generateAsString();
        IdSegmentDistributor distributor = segmentDistributorFactory(new ManualCommitDataSource(dataSource()))
            .create(new IdSegmentDistributorDefinition("manual_commit", name, 100, 10));
        
        assertThat(distributor.nextMaxId(), equalTo(110L));
        assertThat(queryLong(dataSource(), "select last_max_id from cosid where name = ?", distributor.getNamespacedName()), equalTo(110L));
    }
    
    @Test
    void machineDistributeGuardAndRevertAreCommitted() throws SQLException {
        String namespace = MockIdGenerator.usePrefix("manual").generateAsString();
        InstanceId instanceId = InstanceId.of("manual", 1, false);
        InMemoryMachineStateStorage storage = new InMemoryMachineStateStorage();
        LeaseStoreMachineIdDistributor distributor = new LeaseStoreMachineIdDistributor(machineLeaseStore(new ManualCommitDataSource(dataSource())),
            storage, ClockBackwardsSynchronizer.DEFAULT);
        String instanceSql = "select machine_id from cosid_machine where namespace = ? and instance_id = ?";
        
        MachineState machineState = distributor.distribute(namespace, 4, instanceId, Duration.ofMinutes(1));
        assertThat(queryLong(dataSource(), instanceSql, namespace, instanceId.getInstanceId()), equalTo((long) machineState.getMachineId()));
        
        distributor.guard(namespace, instanceId, Duration.ofMinutes(1));
        distributor.revert(namespace, instanceId);
        assertThat(queryLong(dataSource(), instanceSql, namespace, instanceId.getInstanceId()), equalTo(-1L));
    }
}
