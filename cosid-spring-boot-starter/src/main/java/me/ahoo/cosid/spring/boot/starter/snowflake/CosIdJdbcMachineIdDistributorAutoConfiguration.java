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

package me.ahoo.cosid.spring.boot.starter.snowflake;

import me.ahoo.cosid.jdbc.JdbcMachineIdDistributor;
import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.machine.MachineStateStorage;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;

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
@Configuration(proxyBeanMethods = false)
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdSnowflakeEnabled
@ConditionalOnClass(JdbcMachineIdDistributor.class)
@ConditionalOnProperty(value = SnowflakeIdProperties.Machine.Distributor.TYPE, havingValue = "jdbc")
public class CosIdJdbcMachineIdDistributorAutoConfiguration {
    private final SnowflakeIdProperties snowflakeIdProperties;
    
    public CosIdJdbcMachineIdDistributorAutoConfiguration(SnowflakeIdProperties snowflakeIdProperties) {
        this.snowflakeIdProperties = snowflakeIdProperties;
    }
    
    @Bean
    @ConditionalOnMissingBean
    public JdbcMachineIdDistributor jdbcMachineIdDistributor(DataSource dataSource, MachineStateStorage localMachineState, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        //TODO 配置过于复杂？
        if (!snowflakeIdProperties.getMachine().getGuarder().isEnabled()) {
            return new JdbcMachineIdDistributor(dataSource, localMachineState, clockBackwardsSynchronizer);
        }
        return new JdbcMachineIdDistributor(dataSource, localMachineState, clockBackwardsSynchronizer, snowflakeIdProperties.getMachine().getDistributor().getSafeGuardDuration());
    }
    
}
