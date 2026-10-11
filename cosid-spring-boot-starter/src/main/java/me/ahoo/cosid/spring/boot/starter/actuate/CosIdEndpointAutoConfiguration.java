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

package me.ahoo.cosid.spring.boot.starter.actuate;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.EnabledSuffix;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@Slf4j
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnClass(Endpoint.class)
public class CosIdEndpointAutoConfiguration {
    /**
     * Whether the {@code cosid} endpoint has its DELETE operation. Default {@code true} in 3.x, {@code false} in 4.0.
     */
    public static final String DELETE_ENABLED_KEY = CosId.COSID_PREFIX + "actuator.delete" + EnabledSuffix.KEY;

    
    @Bean
    @ConditionalOnMissingBean(CosIdReadOnlyEndpoint.class)
    @ConditionalOnAvailableEndpoint(endpoint = CosIdEndpoint.class)
    @ConditionalOnProperty(value = DELETE_ENABLED_KEY, matchIfMissing = true, havingValue = "true")
    public CosIdEndpoint cosIdEndpoint(IdGeneratorProvider idGeneratorProvider) {
        if (log.isWarnEnabled()) {
            log.warn("The cosid endpoint exposes a DELETE operation that removes id generators at runtime. Turn it off with [{}=false]; "
                + "it will be off by default in 4.0.", DELETE_ENABLED_KEY);
        }
        return new CosIdEndpoint(idGeneratorProvider);
    }
    
    @Bean
    @ConditionalOnMissingBean(CosIdReadOnlyEndpoint.class)
    @ConditionalOnAvailableEndpoint(endpoint = CosIdReadOnlyEndpoint.class)
    @ConditionalOnProperty(value = DELETE_ENABLED_KEY, havingValue = "false")
    public CosIdReadOnlyEndpoint cosIdReadOnlyEndpoint(IdGeneratorProvider idGeneratorProvider) {
        return new CosIdReadOnlyEndpoint(idGeneratorProvider);
    }
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnAvailableEndpoint
    public CosIdGeneratorEndpoint cosIdGeneratorEndpoint(IdGeneratorProvider idGeneratorProvider) {
        return new CosIdGeneratorEndpoint(idGeneratorProvider);
    }
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnAvailableEndpoint
    public CosIdStringGeneratorEndpoint cosIdStringGeneratorEndpoint(IdGeneratorProvider idGeneratorProvider) {
        return new CosIdStringGeneratorEndpoint(idGeneratorProvider);
    }
}
