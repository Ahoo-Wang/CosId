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
import static org.hamcrest.Matchers.equalTo;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InMemoryMachineStateStorage;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.test.machine.lease.MixedLeaseFormatSpec;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Runs {@link MixedLeaseFormatSpec} on a real PostgreSQL: {@link JdbcMachineIdDistributor} as the 3.4 instances.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcPostgresMixedLeaseFormatTest extends MixedLeaseFormatSpec {
    DataSource dataSource;
    JdbcMachineLeaseStore store;
    
    @BeforeAll
    void setup() {
        dataSource = PooledDataSource.of(RealPostgres.createDataSource(), true);
        store = new JdbcMachineLeaseStore(dataSource, JdbcSupport.DEFAULT_QUERY_TIMEOUT, PostgreSqlDialect.SERVER_TIME_SQL);
    }
    
    @Override
    protected MachineIdDistributor v34() {
        return new JdbcMachineIdDistributor(dataSource, new InMemoryMachineStateStorage(), ClockBackwardsSynchronizer.DEFAULT);
    }
    
    @Override
    protected MachineIdDistributor v40() {
        return new LeaseStoreMachineIdDistributor(store, new InMemoryMachineStateStorage(), ClockBackwardsSynchronizer.DEFAULT);
    }
    
    @Override
    protected void takeReleasedAs34WithTheSameTimestamp(String namespace, int machineId, String instanceId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("update cosid_machine set instance_id=? where name=? and instance_id=''")) {
            statement.setString(1, instanceId);
            statement.setString(2, namespacedMachineId(namespace, machineId));
            assertThat(statement.executeUpdate(), equalTo(1));
        } catch (SQLException sqlException) {
            throw new IllegalStateException(sqlException);
        }
    }
}
