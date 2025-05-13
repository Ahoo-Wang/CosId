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
import static org.testcontainers.shaded.org.bouncycastle.oer.OERDefinition.BaseType.Supplier;

import me.ahoo.coapi.spring.boot.starter.CoApiAutoConfiguration;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.proxy.ProxyMachineIdDistributor;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;
import org.springframework.web.client.RestClient;

class CosIdProxyMachineIdDistributorAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    @Test
    void contextLoads() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=proxy")
            .withPropertyValues("cosid.proxy.host" + "=http://localhost:8688")
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withBean(MachineStateStorage.class, () -> MachineStateStorage.IN_MEMORY)
            .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT)
            .withUserConfiguration(UtilAutoConfiguration.class, CosIdAutoConfiguration.class, CosIdHostNameAutoConfiguration.class,
                CoApiAutoConfiguration.class)
            .withUserConfiguration(CosIdProxyMachineIdDistributorAutoConfiguration.class)
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdProxyMachineIdDistributorAutoConfiguration.class)
                    .hasSingleBean(ProxyMachineIdDistributor.class)
                ;
            });
    }
}