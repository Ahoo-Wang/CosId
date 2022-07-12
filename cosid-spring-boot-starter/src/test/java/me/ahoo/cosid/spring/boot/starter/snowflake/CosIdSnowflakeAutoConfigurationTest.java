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

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.SnowflakeId;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineId;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.ManualMachineIdDistributor;
import me.ahoo.cosid.machine.k8s.StatefulSetMachineIdDistributor;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.ConditionalOnCosIdMachineEnabled;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdLifecycleMachineIdDistributor;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdMachineAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;

/**
 * CosIdSnowflakeAutoConfigurationTest .
 *
 * @author ahoo wang
 */
class CosIdSnowflakeAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();
    
    @Test
    void contextLoads() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(ConditionalOnCosIdSnowflakeEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.PREFIX + ".distributor.manual.machineId=1")
            .withUserConfiguration(UtilAutoConfiguration.class,
                CosIdAutoConfiguration.class,
                CosIdMachineAutoConfiguration.class,
                CosIdSnowflakeAutoConfiguration.class)
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdSnowflakeAutoConfiguration.class)
                    .hasSingleBean(SnowflakeIdProperties.class)
                    .hasSingleBean(InstanceId.class)
                    .hasSingleBean(MachineStateStorage.class)
                    .hasSingleBean(ClockBackwardsSynchronizer.class)
                    .hasSingleBean(MachineId.class)
                    .hasSingleBean(CosIdLifecycleMachineIdDistributor.class)
                    .hasSingleBean(SnowflakeId.class)
                ;
            });
    }
    
}
