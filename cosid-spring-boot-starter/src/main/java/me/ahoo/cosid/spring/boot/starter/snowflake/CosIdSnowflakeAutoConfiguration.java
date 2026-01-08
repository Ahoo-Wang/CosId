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

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.GuardDistribute;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.Nullable;

/**
 * CosId Snowflake AutoConfiguration.
 *
 * @author ahoo wang
 */
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdSnowflakeEnabled
@EnableConfigurationProperties(SnowflakeIdProperties.class)
public class CosIdSnowflakeAutoConfiguration {
    private final CosIdProperties cosIdProperties;
    private final MachineProperties machineProperties;
    private final SnowflakeIdProperties snowflakeIdProperties;
    
    public CosIdSnowflakeAutoConfiguration(CosIdProperties cosIdProperties,
                                           MachineProperties machineProperties,
                                           SnowflakeIdProperties snowflakeIdProperties) {
        this.cosIdProperties = cosIdProperties;
        this.machineProperties = machineProperties;
        this.snowflakeIdProperties = snowflakeIdProperties;
    }
    
    @Bean
    public SnowflakeIdBeanRegistrar snowflakeIdBeanRegistrar(final InstanceId instanceId,
                                                             IdGeneratorProvider idGeneratorProvider,
                                                             GuardDistribute guardDistribute,
                                                             ClockBackwardsSynchronizer clockBackwardsSynchronizer,
                                                             ConfigurableApplicationContext applicationContext,
                                                             @Nullable
                                                             CustomizeSnowflakeIdProperties customizeSnowflakeIdProperties
    ) {
        return new SnowflakeIdBeanRegistrar(cosIdProperties,
            machineProperties,
            snowflakeIdProperties,
            instanceId,
            idGeneratorProvider,
            guardDistribute,
            clockBackwardsSynchronizer,
            applicationContext,
            customizeSnowflakeIdProperties);
    }
    
}
