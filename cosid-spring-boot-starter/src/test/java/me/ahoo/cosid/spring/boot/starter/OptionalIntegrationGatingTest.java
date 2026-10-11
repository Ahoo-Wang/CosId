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

package me.ahoo.cosid.spring.boot.starter;

import static org.assertj.core.api.Assertions.assertThat;

import me.ahoo.cosid.machine.HostAddressSupplier;
import me.ahoo.cosid.machine.MachineId;
import me.ahoo.cosid.spring.boot.starter.machine.ConditionalOnCosIdMachineEnabled;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdMachineAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdProxyMachineIdDistributorAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.MachineIdHealthIndicator;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;
import me.ahoo.cosid.spring.boot.starter.segment.ConditionalOnCosIdSegmentEnabled;
import me.ahoo.cosid.spring.boot.starter.segment.CosIdProxySegmentAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Optional integrations apply only when their libraries are on the classpath.
 */
class OptionalIntegrationGatingTest {
    
    private final ApplicationContextRunner machineRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdAutoConfiguration.class, CosIdMachineAutoConfiguration.class))
        .withBean(HostAddressSupplier.class, () -> () -> "10.0.0.10")
        .withBean(MachineId.class, () -> new MachineId(1))
        .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true",
            MachineProperties.PREFIX + ".distributor.manual.machine-id=1",
            MachineProperties.PREFIX + ".guarder.enabled=false");
    
    @Test
    void machineHealthIndicatorNeedsActuatorHealth() {
        machineRunner.run(context -> assertThat(context).hasSingleBean(MachineIdHealthIndicator.class));
        machineRunner
            .withClassLoader(new FilteredClassLoader("org.springframework.boot.health"))
            .run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(MachineIdHealthIndicator.class));
    }
    
    @Test
    void proxyMachineDistributorNeedsTheProxyModule() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CosIdProxyMachineIdDistributorAutoConfiguration.class))
            .withClassLoader(new FilteredClassLoader("me.ahoo.cosid.proxy"))
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true", MachineProperties.Distributor.TYPE + "=proxy")
            .run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(CosIdProxyMachineIdDistributorAutoConfiguration.class));
    }
    
    @Test
    void proxySegmentDistributorNeedsCoApi() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CosIdProxySegmentAutoConfiguration.class))
            .withClassLoader(new FilteredClassLoader("me.ahoo.coapi"))
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true", SegmentIdProperties.Distributor.TYPE + "=proxy")
            .run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(CosIdProxySegmentAutoConfiguration.class));
    }
}
