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

import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

class JdbcMachineLeaseStoreTest {
    
    /**
     * 2025-11-02 01:30:00.250 in New York, the second time (EST, after the clocks went back).
     */
    private static final long REPEATED_HOUR_MILLIS = 1_762_065_000_250L;
    
    /**
     * {@code unix_timestamp(now(3))} goes through the session time zone and returned one hour too low here.
     */
    @Test
    void mysqlServerTimeIgnoresTheSessionTimeZoneInTheRepeatedHour() throws SQLException {
        DataSource dataSource = RealMySql.createDataSource();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            try {
                statement.execute("set time_zone = 'America/New_York'");
            } catch (SQLException noTimeZoneTables) {
                Assumptions.abort("MySQL has no time zone tables: " + noTimeZoneTables.getMessage());
            }
            statement.execute("set timestamp = " + REPEATED_HOUR_MILLIS / 1000 + "." + REPEATED_HOUR_MILLIS % 1000);
            try (ResultSet resultSet = statement.executeQuery(JdbcMachineLeaseStore.MYSQL_SERVER_TIME_SQL)) {
                Assertions.assertTrue(resultSet.next());
                Assertions.assertEquals(REPEATED_HOUR_MILLIS, resultSet.getLong(1));
            }
        }
    }
    
    @Test
    void tableWithoutStableColumnFailsAtConstruction() throws SQLException {
        DataSource dataSource = RealPostgres.createDataSource();
        String schema = "legacy_" + MockIdGenerator.INSTANCE.generateAsString().toLowerCase();
        RealPostgres.execute(dataSource, "create schema " + schema);
        RealPostgres.execute(dataSource, PostgreSqlDialect.INIT_COSID_MACHINE_TABLE_SQL
            .replace("create table if not exists cosid_machine", "create table " + schema + ".cosid_machine")
            .replace("    stable          smallint     not null default 0,\n", ""));
        DataSource legacy = new ManualCommitDataSource(dataSource, schema);
        
        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class,
            () -> new JdbcMachineLeaseStore(legacy, JdbcSupport.DEFAULT_QUERY_TIMEOUT, PostgreSqlDialect.SERVER_TIME_SQL));
        
        Assertions.assertTrue(exception.getMessage().contains(PostgreSqlDialect.ADD_STABLE_COLUMN_SQL), exception.getMessage());
    }
    
    @Test
    void tableFrom34FailsAtConstructionNamingTheAlterAndWorksAfterIt() throws SQLException {
        DataSource dataSource = RealPostgres.createDataSource();
        String schema = "v34_" + MockIdGenerator.INSTANCE.generateAsString().toLowerCase();
        RealPostgres.execute(dataSource, "create schema " + schema);
        RealPostgres.execute(dataSource, PostgreSqlDialect.INIT_COSID_MACHINE_TABLE_SQL
            .replace("create table if not exists cosid_machine", "create table " + schema + ".cosid_machine")
            .replace("    renew_time      bigint       not null default 0,\n", "")
            .replace("    watermark       bigint       not null default 0,\n", "")
            .replace("    lease_version   bigint       not null default 0,\n", ""));
        DataSource v34 = new ManualCommitDataSource(dataSource, schema);
        
        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class,
            () -> new JdbcMachineLeaseStore(v34, JdbcSupport.DEFAULT_QUERY_TIMEOUT, PostgreSqlDialect.SERVER_TIME_SQL));
        Assertions.assertTrue(exception.getMessage().contains(PostgreSqlDialect.ADD_LEASE_COLUMNS_SQL), exception.getMessage());
        
        RealPostgres.execute(dataSource, PostgreSqlDialect.ADD_LEASE_COLUMNS_SQL.replace("alter table cosid_machine", "alter table " + schema + ".cosid_machine"));
        Assertions.assertDoesNotThrow(() -> new JdbcMachineLeaseStore(v34, JdbcSupport.DEFAULT_QUERY_TIMEOUT, PostgreSqlDialect.SERVER_TIME_SQL));
    }
    
    /**
     * A MySQL table from 3.4, as a temporary table on one shared connection, which hides the real one.
     */
    @Test
    void mysqlTableFrom34FailsAtConstructionNamingTheAlter() throws SQLException {
        DataSource realDataSource = RealMySql.createDataSource();
        try (Connection shared = realDataSource.getConnection();
             Statement statement = shared.createStatement()) {
            statement.execute(JdbcMachineIdInitializer.INIT_COSID_MACHINE_TABLE_SQL
                .replace("create table if not exists cosid_machine", "create temporary table cosid_machine")
                .replace("    renew_time      bigint unsigned  not null default 0,\n", "")
                .replace("    watermark       bigint unsigned  not null default 0,\n", "")
                .replace("    lease_version   bigint unsigned  not null default 0,\n", ""));
            Connection unclosable = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> "close".equals(method.getName()) ? null : method.invoke(shared, args));
            DataSource v34 = (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class},
                (proxy, method, args) -> "getConnection".equals(method.getName()) ? unclosable : method.invoke(realDataSource, args));
            
            IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class, () -> new JdbcMachineLeaseStore(v34));
            Assertions.assertTrue(exception.getMessage().contains(JdbcMachineLeaseStore.ADD_LEASE_COLUMNS_SQL), exception.getMessage());
            
            statement.execute(JdbcMachineLeaseStore.ADD_LEASE_COLUMNS_SQL);
            Assertions.assertDoesNotThrow(() -> new JdbcMachineLeaseStore(v34));
        }
    }
    
    /**
     * On PostgreSQL a failed statement aborts the transaction. With auto-commit off on one shared connection, the failed
     * stable-column probe must be rolled back, so the next statement on that connection still runs.
     */
    @Test
    void failedStableColumnProbeDoesNotLeaveTheTransactionAborted() throws SQLException {
        DataSource dataSource = RealPostgres.createDataSource();
        String schema = "probe_" + MockIdGenerator.INSTANCE.generateAsString().toLowerCase();
        RealPostgres.execute(dataSource, "create schema " + schema);
        RealPostgres.execute(dataSource, PostgreSqlDialect.INIT_COSID_MACHINE_TABLE_SQL
            .replace("create table if not exists cosid_machine", "create table " + schema + ".cosid_machine")
            .replace("    stable          smallint     not null default 0,\n", ""));
        try (Connection shared = new ManualCommitDataSource(dataSource, schema).getConnection()) {
            Connection unclosable = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> "close".equals(method.getName()) ? null : method.invoke(shared, args));
            DataSource oneConnection = (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class},
                (proxy, method, args) -> "getConnection".equals(method.getName()) ? unclosable : method.invoke(dataSource, args));
            Assertions.assertFalse(shared.getAutoCommit());
            
            IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class,
                () -> new JdbcMachineLeaseStore(oneConnection, JdbcSupport.DEFAULT_QUERY_TIMEOUT, PostgreSqlDialect.SERVER_TIME_SQL));
            
            Assertions.assertTrue(exception.getMessage().contains(PostgreSqlDialect.ADD_STABLE_COLUMN_SQL), exception.getMessage());
            try (Statement statement = shared.createStatement();
                 ResultSet resultSet = statement.executeQuery("select count(*) from cosid_machine")) {
                Assertions.assertTrue(resultSet.next());
            }
        }
    }
    
    @Test
    void missingTableFailsAtConstructionNamingTheTable() {
        DataSource dataSource = RealPostgres.createDataSource();
        String schema = "empty_" + MockIdGenerator.INSTANCE.generateAsString().toLowerCase();
        Assertions.assertDoesNotThrow(() -> RealPostgres.execute(dataSource, "create schema " + schema));
        DataSource empty = new ManualCommitDataSource(dataSource, schema);
        
        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class,
            () -> new JdbcMachineLeaseStore(empty, JdbcSupport.DEFAULT_QUERY_TIMEOUT, PostgreSqlDialect.SERVER_TIME_SQL));
        
        Assertions.assertTrue(exception.getMessage().contains("does not exist"), exception.getMessage());
    }
}
