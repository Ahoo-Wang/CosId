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

package me.ahoo.cosid.spring.boot.starter.metrics;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdGuarder;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdMachineAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.segment.CosIdSegmentAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.snowflake.CosIdSnowflakeAutoConfiguration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.Set;

/**
 * Registers {@link CosIdMeterBinder} when Micrometer is on the classpath ({@code cosid.metrics.enabled}, default on).
 *
 * @author ahoo wang
 * @since 4.1.0
 */
@AutoConfiguration(after = {CosIdAutoConfiguration.class, CosIdMachineAutoConfiguration.class, CosIdSegmentAutoConfiguration.class,
    CosIdSnowflakeAutoConfiguration.class},
    afterName = {"org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration",
        "org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration"})
@ConditionalOnCosIdEnabled
@ConditionalOnClass(name = CosIdMetricsProperties.METER_REGISTRY_CLASS)
@ConditionalOnBean(type = CosIdMetricsProperties.METER_REGISTRY_CLASS)
@ConditionalOnProperty(value = CosIdMetricsProperties.ENABLED_KEY, havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CosIdMetricsProperties.class)
public class CosIdMetricsAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public CosIdMeterBinder cosIdMeterBinder(IdGeneratorProvider idGeneratorProvider, ObjectProvider<MachineIdGuarder> machineIdGuarder,
                                             ObjectProvider<InstanceId> instanceId, Environment environment) {
        boolean isolated = environment.getProperty(CosIdAutoConfiguration.PROVIDER_ISOLATED_KEY, Boolean.class, false);
        InstanceId ownInstance = instanceId.getIfUnique();
        Set<String> instanceIds = ownInstance == null ? Set.of() : Set.of(ownInstance.getInstanceId());
        return new CosIdMeterBinder(idGeneratorProvider, machineIdGuarder.getIfAvailable(), isolated, instanceIds);
    }
}
