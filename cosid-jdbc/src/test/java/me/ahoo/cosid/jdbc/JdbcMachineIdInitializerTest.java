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

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import javax.sql.DataSource;

/**
 * JdbcMachineIdInitializerTest .
 *
 * @author ahoo wang
 */
class JdbcMachineIdInitializerTest {
    
    
    DataSource dataSource;
    private JdbcMachineIdInitializer machineIdInitializer;
    
    @BeforeEach
    private void setup() {
        dataSource = DataSourceFactory.INSTANCE.createDataSource();
        machineIdInitializer = new JdbcMachineIdInitializer(dataSource);
    }
    
    @SneakyThrows
    @DisabledIfEnvironmentVariable(named = "MYSQL", matches = "5.1")
    @Test
    void initCosIdMachineTable() {
        machineIdInitializer.initCosIdMachineTable();
    }
    
    @DisabledIfEnvironmentVariable(named = "MYSQL", matches = "5.1")
    @Test
    void tryInitCosIdMachineTable() {
        machineIdInitializer.tryInitCosIdMachineTable();
    }
}
