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
import static org.mockito.Mockito.mock;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.proxy.ProxyMachineIdDistributor;
import me.ahoo.cosid.proxy.api.MachineClient;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CosIdProxyMachineIdDistributorAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdProxyMachineIdDistributorAutoConfiguration.class))
        .withBean(MachineClient.class, () -> mock(MachineClient.class))
        .withBean(MachineStateStorage.class, () -> MachineStateStorage.IN_MEMORY)
        .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT);

    @Test
    void createsProxyMachineIdDistributorWhenTypeIsProxy() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=proxy")
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdProxyMachineIdDistributorAutoConfiguration.class)
                    .hasSingleBean(ProxyMachineIdDistributor.class)
                ;
            });
    }

    @Test
    void backsOffWhenUserProvidesProxyMachineIdDistributor() {
        ProxyMachineIdDistributor userDistributor = mock(ProxyMachineIdDistributor.class);

        this.contextRunner
            .withBean(ProxyMachineIdDistributor.class, () -> userDistributor)
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=proxy")
            .run(context -> assertThat(context)
                .hasSingleBean(ProxyMachineIdDistributor.class)
                .getBean(ProxyMachineIdDistributor.class)
                .isSameAs(userDistributor));
    }

    @Test
    void doesNotCreateDistributorWhenMachineIsDisabled() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=false")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=proxy")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdProxyMachineIdDistributorAutoConfiguration.class)
                .doesNotHaveBean(ProxyMachineIdDistributor.class));
    }

    @Test
    void doesNotCreateDistributorWhenTypeDoesNotMatch() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=manual")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdProxyMachineIdDistributorAutoConfiguration.class)
                .doesNotHaveBean(ProxyMachineIdDistributor.class));
    }

    @Test
    void failsFastWhenCoApiRestClientBuilderIsMissing() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CosIdProxyMachineIdDistributorAutoConfiguration.class))
            .withBean(MachineStateStorage.class, () -> MachineStateStorage.IN_MEMORY)
            .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT)
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=proxy")
            .run(context -> assertThat(hasCauseMessage(context.getStartupFailure(), "RestClient$Builder"))
                .isTrue());
    }

    private static boolean hasCauseMessage(Throwable throwable, String message) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(message)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
