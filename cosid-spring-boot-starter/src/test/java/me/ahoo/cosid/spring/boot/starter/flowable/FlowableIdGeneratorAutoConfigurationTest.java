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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import me.ahoo.cosid.flowable.FlowableIdGenerator;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FlowableIdGeneratorAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FlowableIdGeneratorAutoConfiguration.class));

    @Test
    void createsConfigurerWhenCosIdAndFlowableAreAvailable() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(EngineConfigurationConfigurer.class);

            SpringProcessEngineConfiguration engineConfiguration = mock(SpringProcessEngineConfiguration.class);
            context.getBean(EngineConfigurationConfigurer.class).configure(engineConfiguration);

            verify(engineConfiguration).setIdGenerator(isA(FlowableIdGenerator.class));
        });
    }

    @Test
    void doesNotCreateConfigurerWhenCosIdIsDisabled() {
        this.contextRunner
            .withPropertyValues("cosid.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(EngineConfigurationConfigurer.class));
    }

    @Test
    void doesNotCreateConfigurerWhenFlowableAdapterIsMissing() {
        this.contextRunner
            .withClassLoader(new FilteredClassLoader(FlowableIdGenerator.class))
            .run(context -> assertThat(context).doesNotHaveBean(EngineConfigurationConfigurer.class));
    }

    @Test
    void doesNotCreateConfigurerWhenFlowableSpringIntegrationIsMissing() {
        this.contextRunner
            .withClassLoader(new FilteredClassLoader(
                SpringProcessEngineConfiguration.class,
                EngineConfigurationConfigurer.class
            ))
            .run(context -> assertThat(context).doesNotHaveBean(EngineConfigurationConfigurer.class));
    }
}
