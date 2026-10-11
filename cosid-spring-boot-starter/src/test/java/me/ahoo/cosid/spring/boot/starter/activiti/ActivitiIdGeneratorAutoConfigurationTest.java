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

import static org.assertj.core.api.Assertions.assertThat;

import me.ahoo.cosid.activiti.ActivitiIdGenerator;
import me.ahoo.cosid.flowable.FlowableIdGenerator;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;
import me.ahoo.cosid.test.MockIdGenerator;

import org.activiti.engine.impl.cfg.IdGenerator;
import org.activiti.spring.SpringProcessEngineConfiguration;
import org.activiti.spring.boot.ProcessEngineConfigurationConfigurer;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ActivitiIdGeneratorAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdAutoConfiguration.class, ActivitiIdGeneratorAutoConfiguration.class));
    
    @Test
    void createsConfigurerWhenCosIdIsEnabled() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdEnabled.ENABLED_KEY + "=true")
            .run(context -> {
                AssertionsForInterfaceTypes.assertThat(context)
                    .hasSingleBean(ActivitiIdGeneratorAutoConfiguration.class)
                    .hasSingleBean(ProcessEngineConfigurationConfigurer.class)
                ;
            });
    }

    @Test
    void createsConfigurerWhenFlowableIdGeneratorIsMissing() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdEnabled.ENABLED_KEY + "=true")
            .withClassLoader(new FilteredClassLoader(FlowableIdGenerator.class))
            .run(context -> {
                AssertionsForInterfaceTypes.assertThat(context)
                    .hasSingleBean(ActivitiIdGeneratorAutoConfiguration.class)
                    .hasSingleBean(ProcessEngineConfigurationConfigurer.class)
                ;
            });
    }

    @Test
    void doesNotCreateConfigurerWhenActivitiIdGeneratorIsMissing() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdEnabled.ENABLED_KEY + "=true")
            .withClassLoader(new FilteredClassLoader(ActivitiIdGenerator.class))
            .run(context -> {
                AssertionsForInterfaceTypes.assertThat(context)
                    .doesNotHaveBean(ActivitiIdGeneratorAutoConfiguration.class)
                    .doesNotHaveBean(ProcessEngineConfigurationConfigurer.class)
                ;
            });
    }

    @Test
    void doesNotCreateConfigurerWhenActivitiSpringIntegrationIsMissing() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdEnabled.ENABLED_KEY + "=true")
            .withClassLoader(new FilteredClassLoader(
                SpringProcessEngineConfiguration.class,
                ProcessEngineConfigurationConfigurer.class
            ))
            .run(context -> {
                AssertionsForInterfaceTypes.assertThat(context)
                    .doesNotHaveBean(ActivitiIdGeneratorAutoConfiguration.class)
                    .doesNotHaveBean(ProcessEngineConfigurationConfigurer.class)
                ;
            });
    }

    @Test
    void doesNotCreateConfigurerWhenCosIdIsDisabled() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdEnabled.ENABLED_KEY + "=false")
            .run(context -> {
                AssertionsForInterfaceTypes.assertThat(context)
                    .doesNotHaveBean(ActivitiIdGeneratorAutoConfiguration.class)
                    .doesNotHaveBean(ProcessEngineConfigurationConfigurer.class)
                ;
            });
    }

    @Test
    void configuresIdGeneratorFromContextProvider() {
        this.contextRunner
            .withPropertyValues(CosIdAutoConfiguration.PROVIDER_ISOLATED_KEY + "=true")
            .run(context -> {
                context.getBean(IdGeneratorProvider.class).setShare(MockIdGenerator.usePrefix("ctx_"));
                SpringProcessEngineConfiguration engineConfiguration = new SpringProcessEngineConfiguration();
                context.getBean(ProcessEngineConfigurationConfigurer.class).configure(engineConfiguration);

                IdGenerator idGenerator = engineConfiguration.getIdGenerator();
                assertThat(idGenerator).isInstanceOf(ActivitiIdGenerator.class);
                assertThat(idGenerator.getNextId()).startsWith("ctx_");
            });
    }
}
