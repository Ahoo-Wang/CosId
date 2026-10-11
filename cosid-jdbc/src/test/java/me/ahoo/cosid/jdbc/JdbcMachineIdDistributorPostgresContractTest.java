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

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InMemoryMachineStateStorage;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.test.machine.distributor.MachineIdDistributorSpec;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Duration;

/**
 * Runs {@link MachineIdDistributorSpec} against a real PostgreSQL with {@link PostgreSqlDialect}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcMachineIdDistributorPostgresContractTest extends MachineIdDistributorSpec {
    DataSource dataSource;
    
    @BeforeAll
    void setup() {
        dataSource = RealPostgres.createDataSource();
    }
    
    @Override
    protected MachineIdDistributor getDistributor() {
        return new JdbcMachineIdDistributor(dataSource, MachineStateStorage.IN_MEMORY, ClockBackwardsSynchronizer.DEFAULT);
    }
    
    /**
     * On PostgreSQL a failed statement aborts the transaction. A table without the stable column makes the probe fail;
     * with auto-commit off, the distributor must still be able to run its next statements on the same connection.
     */
    @Test
    void failedStableColumnProbeDoesNotAbortTheTransaction() throws SQLException {
        String schema = "legacy_" + MockIdGenerator.INSTANCE.generateAsString().toLowerCase();
        RealPostgres.execute(dataSource, "create schema " + schema);
        RealPostgres.execute(dataSource, PostgreSqlDialect.INIT_COSID_MACHINE_TABLE_SQL
            .replace("create table if not exists cosid_machine", "create table " + schema + ".cosid_machine")
            .replace("    stable          smallint     not null default 0,\n", ""));
        DataSource legacyNoAutoCommit = new ManualCommitDataSource(dataSource, schema);
        JdbcMachineIdDistributor distributor = new JdbcMachineIdDistributor(legacyNoAutoCommit, new InMemoryMachineStateStorage(),
            ClockBackwardsSynchronizer.DEFAULT);
        
        int machineId = distributor.distribute("legacy", 4, InstanceId.of("host", 1, true), Duration.ofMinutes(1)).getMachineId();
        
        Assertions.assertEquals(0, machineId);
    }
}
