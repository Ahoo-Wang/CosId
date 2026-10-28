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

package me.ahoo.cosid.spring.autoconfigure;

import me.ahoo.cosid.runtime.CosIdRuntime;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The {@code cosid} endpoint and health indicator, each when its Actuator module is present.
 */
@AutoConfiguration(after = CosIdAutoConfiguration.class)
@ConditionalOnBean(CosIdRuntime.class)
public class CosIdActuatorConfiguration {
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
        "org.springframework.boot.actuate.endpoint.annotation.Endpoint",
        "org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint"
    })
    static class Endpoints {
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnAvailableEndpoint(CosIdEndpoint.class)
        CosIdEndpoint cosIdEndpoint(CosIdRuntime runtime) {
            return new CosIdEndpoint(runtime);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    static class HealthIndicators {
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnEnabledHealthIndicator("cosid")
        // The bean name gives the contributor name: /actuator/health/cosid, like management.health.cosid.enabled (R1-spring-05).
        CosIdHealthIndicator cosidHealthIndicator(ObjectProvider<CosIdRuntime> runtime) {
            return new CosIdHealthIndicator(runtime);
        }
    }
}
