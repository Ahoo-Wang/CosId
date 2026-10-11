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
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.logging.Logger;

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
        DataSource legacyNoAutoCommit = new SchemaDataSource(dataSource, schema);
        JdbcMachineIdDistributor distributor = new JdbcMachineIdDistributor(legacyNoAutoCommit, new InMemoryMachineStateStorage(),
            ClockBackwardsSynchronizer.DEFAULT);
        
        int machineId = distributor.distribute("legacy", 4, InstanceId.of("host", 1, true), Duration.ofMinutes(1)).getMachineId();
        
        Assertions.assertEquals(0, machineId);
    }
    
    /**
     * Connections with auto-commit off whose search path is {@code schema}.
     */
    private static final class SchemaDataSource implements DataSource {
        private final DataSource delegate;
        private final String schema;
        
        private SchemaDataSource(DataSource delegate, String schema) {
            this.delegate = delegate;
            this.schema = schema;
        }
        
        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = delegate.getConnection();
            connection.setSchema(schema);
            connection.setAutoCommit(false);
            return connection;
        }
        
        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }
        
        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }
        
        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }
        
        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }
        
        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }
        
        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }
        
        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }
        
        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }
    }
}
