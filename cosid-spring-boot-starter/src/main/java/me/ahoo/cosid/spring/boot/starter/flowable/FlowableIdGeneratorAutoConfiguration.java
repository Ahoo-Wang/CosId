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

package me.ahoo.cosid.spring.boot.starter.flowable;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.flowable.FlowableIdGenerator;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.EnabledSuffix;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Flowable IdGenerator Auto Configuration.
 *
 * @author ahoo wang
 */
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnClass({
    FlowableIdGenerator.class,
    SpringProcessEngineConfiguration.class,
    EngineConfigurationConfigurer.class
})
@ConditionalOnProperty(value = FlowableIdGeneratorAutoConfiguration.ENABLED_KEY, matchIfMissing = true, havingValue = "true")
public class FlowableIdGeneratorAutoConfiguration {
    /**
     * Turns the Flowable id generator off with {@code false}. Default {@code true}.
     */
    public static final String ENABLED_KEY = CosId.COSID_PREFIX + "flowable" + EnabledSuffix.KEY;
    /**
     * The id generator of Flowable's ids. Unset, the {@code FlowableIdGenerator#ID_KEY} system property's, else the shared one.
     *
     * @since 4.1.0
     */
    public static final String GENERATOR_NAME_KEY = CosId.COSID_PREFIX + "flowable.generator-name";

    
    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> cosidFlowableEngineConfigurationConfigurer(IdGeneratorProvider idGeneratorProvider,
                                                                                    Environment environment) {
        return new CosIdEngineConfigurationConfigurer(idGeneratorProvider, environment.getProperty(GENERATOR_NAME_KEY));
    }
    
    static class CosIdEngineConfigurationConfigurer implements EngineConfigurationConfigurer<SpringProcessEngineConfiguration> {
        private final IdGeneratorProvider idGeneratorProvider;
        private final @Nullable String generatorName;

        CosIdEngineConfigurationConfigurer(IdGeneratorProvider idGeneratorProvider, @Nullable String generatorName) {
            this.idGeneratorProvider = idGeneratorProvider;
            this.generatorName = generatorName;
        }

        @Override
        public void configure(SpringProcessEngineConfiguration engineConfiguration) {
            engineConfiguration.setIdGenerator(generatorName == null || generatorName.isBlank()
                ? new FlowableIdGenerator(idGeneratorProvider) : new FlowableIdGenerator(generatorName, idGeneratorProvider));
        }
    }
}
