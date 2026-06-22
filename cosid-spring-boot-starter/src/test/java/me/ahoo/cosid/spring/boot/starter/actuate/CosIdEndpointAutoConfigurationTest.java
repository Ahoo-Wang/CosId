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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import me.ahoo.cosid.provider.IdGeneratorProvider;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CosIdEndpointAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdEndpointAutoConfiguration.class))
        .withBean(IdGeneratorProvider.class, () -> mock(IdGeneratorProvider.class));

    @Test
    void createsAllEndpointsWhenActuatorEndpointApiIsPresent() {
        this.contextRunner.run(context -> assertThat(context)
            .hasSingleBean(CosIdEndpoint.class)
            .hasSingleBean(CosIdGeneratorEndpoint.class)
            .hasSingleBean(CosIdStringGeneratorEndpoint.class));
    }

    @Test
    void backsOffForUserProvidedEndpointsIndependently() {
        CosIdEndpoint cosIdEndpoint = mock(CosIdEndpoint.class);
        CosIdGeneratorEndpoint generatorEndpoint = mock(CosIdGeneratorEndpoint.class);
        CosIdStringGeneratorEndpoint stringGeneratorEndpoint = mock(CosIdStringGeneratorEndpoint.class);

        this.contextRunner
            .withBean(CosIdEndpoint.class, () -> cosIdEndpoint)
            .withBean(CosIdGeneratorEndpoint.class, () -> generatorEndpoint)
            .withBean(CosIdStringGeneratorEndpoint.class, () -> stringGeneratorEndpoint)
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdEndpoint.class)
                    .hasSingleBean(CosIdGeneratorEndpoint.class)
                    .hasSingleBean(CosIdStringGeneratorEndpoint.class);
                assertThat(context.getBean(CosIdEndpoint.class)).isSameAs(cosIdEndpoint);
                assertThat(context.getBean(CosIdGeneratorEndpoint.class)).isSameAs(generatorEndpoint);
                assertThat(context.getBean(CosIdStringGeneratorEndpoint.class)).isSameAs(stringGeneratorEndpoint);
            });
    }

    @Test
    void doesNotCreateEndpointsWhenCosIdIsDisabled() {
        this.contextRunner
            .withPropertyValues("cosid.enabled=false")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdEndpoint.class)
                .doesNotHaveBean(CosIdGeneratorEndpoint.class)
                .doesNotHaveBean(CosIdStringGeneratorEndpoint.class));
    }

    @Test
    void doesNotCreateEndpointsWhenActuatorEndpointApiIsMissing() {
        this.contextRunner
            .withClassLoader(new FilteredClassLoader(Endpoint.class))
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdEndpoint.class)
                .doesNotHaveBean(CosIdGeneratorEndpoint.class)
                .doesNotHaveBean(CosIdStringGeneratorEndpoint.class));
    }
}
