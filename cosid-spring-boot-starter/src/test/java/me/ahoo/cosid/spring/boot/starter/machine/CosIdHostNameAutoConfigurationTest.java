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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import me.ahoo.cosid.machine.HostAddressSupplier;
import me.ahoo.cosid.machine.LocalHostAddressSupplier;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.commons.util.InetUtils;

class CosIdHostNameAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdHostNameAutoConfiguration.class));

    @Test
    void createsLocalHostAddressSupplierByDefaultWithoutResolvingAddressInTest() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(HostAddressSupplier.class);
            assertThat(context.getBean(HostAddressSupplier.class)).isSameAs(LocalHostAddressSupplier.INSTANCE);
        });
    }

    @Test
    void backsOffWhenUserProvidesHostAddressSupplier() {
        HostAddressSupplier supplier = () -> "10.0.0.9";

        this.contextRunner
            .withBean(HostAddressSupplier.class, () -> supplier)
            .run(context -> assertThat(context.getBean(HostAddressSupplier.class)).isSameAs(supplier));
    }

    @Test
    void createsCloudHostAddressSupplierWhenInetUtilsBeanExists() {
        InetUtils.HostInfo hostInfo = new InetUtils.HostInfo();
        hostInfo.setIpAddress("10.0.0.8");
        InetUtils inetUtils = mock(InetUtils.class);
        when(inetUtils.findFirstNonLoopbackHostInfo()).thenReturn(hostInfo);

        this.contextRunner
            .withBean(InetUtils.class, () -> inetUtils)
            .run(context -> {
                assertThat(context).hasSingleBean(CosIdHostNameAutoConfiguration.CloudUtilHostAddressSupplier.class);
                assertThat(context.getBean(HostAddressSupplier.class).getHostAddress()).isEqualTo("10.0.0.8");
            });
    }

    @Test
    void doesNotCreateHostSupplierWhenCosIdIsDisabled() {
        this.contextRunner
            .withPropertyValues("cosid.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(HostAddressSupplier.class));
    }
}
