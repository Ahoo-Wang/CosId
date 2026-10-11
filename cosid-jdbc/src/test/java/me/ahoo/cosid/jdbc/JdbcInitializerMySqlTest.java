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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.sql.SQLIntegrityConstraintViolationException;

/**
 * {@link JdbcIdSegmentInitializer} and {@link JdbcMachineIdInitializer} on a real MySQL.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcInitializerMySqlTest {
    DataSource realDataSource;
    RecordingDataSource dataSource;
    
    @BeforeAll
    void setupDataSource() {
        realDataSource = RealMySql.createDataSource();
    }
    
    @BeforeEach
    void setup() {
        dataSource = new RecordingDataSource(realDataSource);
    }
    
    private static String uniqueTable(String prefix) {
        return prefix + "_" + MockIdGenerator.INSTANCE.generateAsString().toLowerCase();
    }
    
    @Test
    void initCosIdTableIsIdempotent() throws Exception {
        JdbcIdSegmentInitializer initializer = new JdbcIdSegmentInitializer(dataSource);
        
        assertThat(initializer.initCosIdTable(), equalTo(0));
        assertThat(initializer.tryInitCosIdTable(), equalTo(true));
        assertThat(dataSource.getExecutedSql(), contains(JdbcIdSegmentInitializer.INIT_COSID_TABLE_SQL, JdbcIdSegmentInitializer.INIT_COSID_TABLE_SQL));
    }
    
    @Test
    void tryInitCosIdTableWhenWrongSql() {
        assertThat(new JdbcIdSegmentInitializer("WrongSql", JdbcIdSegmentInitializer.INIT_ID_SEGMENT_SQL, dataSource).tryInitCosIdTable(), equalTo(false));
    }
    
    @Test
    void initIdSegmentRejectsADuplicate() throws Exception {
        String name = MockIdGenerator.INSTANCE.generateAsString();
        JdbcIdSegmentInitializer initializer = new JdbcIdSegmentInitializer(dataSource);
        
        assertThat(initializer.initIdSegment(name, 0), equalTo(1));
        Assertions.assertThrows(SQLIntegrityConstraintViolationException.class, () -> initializer.initIdSegment(name, 0));
        assertThat(initializer.tryInitIdSegment(name, 0), equalTo(false));
    }
    
    @Test
    void tryInitIdSegmentWhenWrongSql() {
        JdbcIdSegmentInitializer initializer = new JdbcIdSegmentInitializer(JdbcIdSegmentInitializer.INIT_COSID_TABLE_SQL, "WrongSql", dataSource);
        
        assertThat(initializer.tryInitIdSegment("wrong-sql", 0), equalTo(false));
        assertThat(dataSource.getExecutedSql(), contains("WrongSql"));
    }
    
    @Test
    void initCosIdMachineTableRunsOnlyTheTableDdlByDefault() throws Exception {
        new JdbcMachineIdInitializer(dataSource).initCosIdMachineTable();
        
        // The default index DDLs are empty (indexes are created inline by the table DDL), so they are skipped.
        assertThat(dataSource.getExecutedSql(), contains(JdbcMachineIdInitializer.INIT_COSID_MACHINE_TABLE_SQL));
    }
    
    @Test
    void tryInitCosIdMachineTableWhenWrongSql() {
        assertThat(new JdbcMachineIdInitializer(dataSource, "WrongSql", "WrongSql", "WrongSql").tryInitCosIdMachineTable(), equalTo(false));
    }
    
    @Test
    void customMachineInitSqlIsPassedThroughVerbatim() {
        String table = uniqueTable("cosid_machine_custom");
        String tableSql = "create table " + table + " (namespace varchar(100), instance_id varchar(100))";
        String namespaceIdxSql = "create index idx_namespace on " + table + " (namespace)";
        String instanceIdIdxSql = "create index idx_instance_id on " + table + " (instance_id)";
        
        assertThat(new JdbcMachineIdInitializer(dataSource, tableSql, namespaceIdxSql, instanceIdIdxSql).tryInitCosIdMachineTable(), equalTo(true));
        assertThat(dataSource.getExecutedSql(), contains(tableSql, namespaceIdxSql, instanceIdIdxSql));
    }
}
