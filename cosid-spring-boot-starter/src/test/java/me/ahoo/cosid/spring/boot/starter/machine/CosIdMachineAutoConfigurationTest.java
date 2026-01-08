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

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineId;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.k8s.StatefulSetMachineIdDistributor;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;

class CosIdMachineAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();
    
    @Test
    @SetEnvironmentVariable(
        key = StatefulSetMachineIdDistributor.HOSTNAME_KEY,
        value = "cosid-host-6")
    void contextLoadsWhenMachineIdDistributorIsStatefulSet() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=stateful_set")
            .withUserConfiguration(UtilAutoConfiguration.class,
                CosIdAutoConfiguration.class,
                CosIdHostNameAutoConfiguration.class,
                CosIdMachineAutoConfiguration.class)
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdMachineAutoConfiguration.class)
                    .hasSingleBean(InstanceId.class)
                    .hasSingleBean(MachineStateStorage.class)
                    .hasSingleBean(ClockBackwardsSynchronizer.class)
                    .hasSingleBean(StatefulSetMachineIdDistributor.class)
                    .hasSingleBean(CosIdMachineIdLifecycle.class)
                    .hasSingleBean(MachineId.class)
                ;
            });
    }
}
