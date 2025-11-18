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

package me.ahoo.cosid.spring.boot.starter.machine;

import me.ahoo.cosid.jdbc.JdbcMachineIdDistributor;
import me.ahoo.cosid.jdbc.JdbcMachineIdInitializer;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.snowflake.ConditionalOnCosIdSnowflakeEnabled;
import me.ahoo.cosid.spring.boot.starter.snowflake.SnowflakeIdProperties;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * CosId Jdbc MachineIdDistributor AutoConfiguration.
 *
 * @author ahoo wang
 */
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdMachineEnabled
@ConditionalOnClass(JdbcMachineIdDistributor.class)
@ConditionalOnProperty(value = MachineProperties.Distributor.TYPE, havingValue = "jdbc")
public class CosIdJdbcMachineIdDistributorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JdbcMachineIdDistributor jdbcMachineIdDistributor(DataSource dataSource, MachineStateStorage localMachineState, 
        ClockBackwardsSynchronizer clockBackwardsSynchronizer, MachineProperties machineProperties) {
        final MachineProperties.Jdbc jdbc = machineProperties.getDistributor().getJdbc();
        if (jdbc.isEndableJdbcMachineIdInitializer()) {
            new JdbcMachineIdInitializer(dataSource, jdbc.getInitCosIdMachineTableSql(), 
                jdbc.getInitNamespaceIdxSql(), jdbc.getInitInstanceIdIdxSql())
                .tryInitCosIdMachineTable();
        }
        return new JdbcMachineIdDistributor(dataSource, localMachineState, clockBackwardsSynchronizer);
    }
}
