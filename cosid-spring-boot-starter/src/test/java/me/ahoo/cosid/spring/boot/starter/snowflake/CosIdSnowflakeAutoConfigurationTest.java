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

import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.SnowflakeId;
import me.ahoo.cosid.snowflake.machine.InstanceId;
import me.ahoo.cosid.snowflake.machine.MachineId;
import me.ahoo.cosid.snowflake.machine.MachineStateStorage;
import me.ahoo.cosid.snowflake.machine.ManualMachineIdDistributor;
import me.ahoo.cosid.snowflake.machine.k8s.StatefulSetMachineIdDistributor;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;

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
            .withPropertyValues(ConditionalOnCosIdSnowflakeEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SnowflakeIdProperties.PREFIX + ".machine.distributor.manual.machineId=1")
            .withUserConfiguration(UtilAutoConfiguration.class, CosIdAutoConfiguration.class, CosIdSnowflakeAutoConfiguration.class)
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdSnowflakeAutoConfiguration.class)
                    .hasSingleBean(SnowflakeIdProperties.class)
                    .hasSingleBean(InstanceId.class)
                    .hasSingleBean(MachineStateStorage.class)
                    .hasSingleBean(ClockBackwardsSynchronizer.class)
                    .hasSingleBean(ManualMachineIdDistributor.class)
                    .hasSingleBean(CosIdLifecycleMachineIdDistributor.class)
                    .hasSingleBean(SnowflakeId.class)
                    .hasSingleBean(MachineId.class)
                ;
            });
    }
    
    @Test
    @SetEnvironmentVariable(
        key = StatefulSetMachineIdDistributor.HOSTNAME_KEY,
        value = "cosid-host-6")
    void contextLoadsWhenMachineIdDistributorIsStatefulSet() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSnowflakeEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SnowflakeIdProperties.PREFIX + ".machine.distributor.type=stateful_set")
            .withUserConfiguration(UtilAutoConfiguration.class, CosIdAutoConfiguration.class, CosIdSnowflakeAutoConfiguration.class)
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdSnowflakeAutoConfiguration.class)
                    .hasSingleBean(SnowflakeIdProperties.class)
                    .hasSingleBean(InstanceId.class)
                    .hasSingleBean(MachineStateStorage.class)
                    .hasSingleBean(ClockBackwardsSynchronizer.class)
                    .hasSingleBean(StatefulSetMachineIdDistributor.class)
                    .hasSingleBean(CosIdLifecycleMachineIdDistributor.class)
                    .hasSingleBean(SnowflakeId.class)
                    .hasSingleBean(MachineId.class)
                ;
            });
    }
}
