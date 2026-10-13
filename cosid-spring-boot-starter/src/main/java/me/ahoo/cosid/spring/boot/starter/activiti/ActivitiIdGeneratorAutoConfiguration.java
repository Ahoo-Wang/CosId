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

package me.ahoo.cosid.spring.boot.starter.activiti;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.activiti.ActivitiIdGenerator;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.EnabledSuffix;

import org.activiti.spring.SpringProcessEngineConfiguration;
import org.activiti.spring.boot.ProcessEngineConfigurationConfigurer;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Activiti IdGenerator Auto Configuration.
 *
 * @author ahoo wang
 */
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnClass({
    ActivitiIdGenerator.class,
    SpringProcessEngineConfiguration.class,
    ProcessEngineConfigurationConfigurer.class
})
@ConditionalOnProperty(value = ActivitiIdGeneratorAutoConfiguration.ENABLED_KEY, matchIfMissing = true, havingValue = "true")
public class ActivitiIdGeneratorAutoConfiguration {
    /**
     * Turns the Activiti id generator off with {@code false}. Default {@code true}.
     */
    public static final String ENABLED_KEY = CosId.COSID_PREFIX + "activiti" + EnabledSuffix.KEY;
    /**
     * The id generator of Activiti's ids. Unset, the {@code ActivitiIdGenerator#ID_KEY} system property's, else the shared one.
     *
     * @since 4.1.0
     */
    public static final String GENERATOR_NAME_KEY = CosId.COSID_PREFIX + "activiti.generator-name";

    
    @Bean
    public ProcessEngineConfigurationConfigurer cosidActivitiProcessEngineConfigurationConfigurer(IdGeneratorProvider idGeneratorProvider,
                                                                                    Environment environment) {
        return new ActivitiIdGeneratorAutoConfiguration.CosIdProcessEngineConfigurationConfigurer(idGeneratorProvider, environment.getProperty(GENERATOR_NAME_KEY));
    }
    
    static class CosIdProcessEngineConfigurationConfigurer implements ProcessEngineConfigurationConfigurer {
        private final IdGeneratorProvider idGeneratorProvider;
        private final @Nullable String generatorName;

        CosIdProcessEngineConfigurationConfigurer(IdGeneratorProvider idGeneratorProvider, @Nullable String generatorName) {
            this.idGeneratorProvider = idGeneratorProvider;
            this.generatorName = generatorName;
        }

        @Override
        public void configure(SpringProcessEngineConfiguration engineConfiguration) {
            engineConfiguration.setIdGenerator(generatorName == null || generatorName.isBlank()
                ? new ActivitiIdGenerator(idGeneratorProvider) : new ActivitiIdGenerator(generatorName, idGeneratorProvider));
        }
    }
}
