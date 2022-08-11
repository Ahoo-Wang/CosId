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

package me.ahoo.cosid.spring.boot.starter.cosid;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

import me.ahoo.cosid.cosid.CosIdGenerator;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineId;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.snowflake.SnowflakeId;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.ConditionalOnCosIdMachineEnabled;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdLifecycleMachineIdDistributor;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdMachineAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;
import me.ahoo.cosid.spring.boot.starter.snowflake.ConditionalOnCosIdSnowflakeEnabled;
import me.ahoo.cosid.spring.boot.starter.snowflake.CosIdSnowflakeAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.snowflake.SnowflakeIdProperties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;

class CosIdGeneratorAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();
    
    @Test
    void contextLoads() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(ConditionalOnCosIdGeneratorEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.PREFIX + ".distributor.manual.machineId=1")
            .withUserConfiguration(UtilAutoConfiguration.class,
                CosIdAutoConfiguration.class,
                CosIdMachineAutoConfiguration.class,
                CosIdGeneratorAutoConfiguration.class)
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdGeneratorAutoConfiguration.class)
                    .hasSingleBean(CosIdGeneratorProperties.class)
                    .hasSingleBean(InstanceId.class)
                    .hasSingleBean(MachineStateStorage.class)
                    .hasSingleBean(ClockBackwardsSynchronizer.class)
                    .hasSingleBean(MachineId.class)
                    .hasSingleBean(CosIdLifecycleMachineIdDistributor.class)
                    .hasSingleBean(CosIdGenerator.class)
                ;
            });
    }
}
