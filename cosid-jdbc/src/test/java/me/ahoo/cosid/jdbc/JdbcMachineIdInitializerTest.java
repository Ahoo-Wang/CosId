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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * JdbcMachineIdInitializerTest .
 *
 * @author ahoo wang
 */
class JdbcMachineIdInitializerTest {
    
    
    InMemoryJdbcDataSource dataSource;
    private JdbcMachineIdInitializer machineIdInitializer;
    
    @BeforeEach
    void setup() {
        dataSource = DataSourceFactory.INSTANCE.createDataSource();
        machineIdInitializer = new JdbcMachineIdInitializer(dataSource);
    }
    
    @SneakyThrows
    @Test
    void initCosIdMachineTable() {
        machineIdInitializer.initCosIdMachineTable();

        assertThat(dataSource.isCosIdMachineTableInitialized(), equalTo(true));
        // The default index DDLs are empty (indexes are created inline by the table DDL),
        // so only the table-creation statement is executed.
        assertThat(dataSource.getExecutedSql(), hasSize(1));
    }
    
    @Test
    void tryInitCosIdMachineTable() {
        assertThat(machineIdInitializer.tryInitCosIdMachineTable(), equalTo(true));

        JdbcMachineIdInitializer wrongSqlInitializer = new JdbcMachineIdInitializer(dataSource, "WrongSql", "WrongSql", "WrongSql");
        assertThat(wrongSqlInitializer.tryInitCosIdMachineTable(), equalTo(false));
    }

    @Test
    void customInitSqlIsPassedThroughVerbatim() {
        String customTableSql = "create table if not exists cosid_machine_custom (id int)";
        String customNamespaceIdxSql = "create index if not exists idx_namespace on cosid_machine_custom (namespace)";
        String customInstanceIdIdxSql = "create index if not exists idx_instance_id on cosid_machine_custom (instance_id)";
        JdbcMachineIdInitializer customInitializer = new JdbcMachineIdInitializer(
            dataSource, customTableSql, customNamespaceIdxSql, customInstanceIdIdxSql);

        assertThat(customInitializer.tryInitCosIdMachineTable(), equalTo(true));
        assertThat(dataSource.getExecutedSql(), hasItem(customTableSql));
        assertThat(dataSource.getExecutedSql(), hasItem(customNamespaceIdxSql));
        assertThat(dataSource.getExecutedSql(), hasItem(customInstanceIdIdxSql));
        assertThat(dataSource.getExecutedSql(), hasSize(3));
    }

    @Test
    void emptyIndexSqlIsSkippedWithoutError() {
        // Indexes are created inline by the table DDL; the standalone index DDLs default to empty
        // and are skipped by initCosIdMachineTable() without executing empty statements.
        JdbcMachineIdInitializer initializer = new JdbcMachineIdInitializer(
            dataSource, JdbcMachineIdInitializer.INIT_COSID_MACHINE_TABLE_SQL, "", "");

        assertThat(initializer.tryInitCosIdMachineTable(), equalTo(true));
        assertThat(dataSource.isCosIdMachineTableInitialized(), equalTo(true));
        // Only the table-creation statement runs; the two empty index DDLs are skipped.
        assertThat(dataSource.getExecutedSql(), hasSize(1));
    }
}
