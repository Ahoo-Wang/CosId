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

package me.ahoo.cosid.spring.boot.starter.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import me.ahoo.cosid.annotation.CosId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;

@ExtendWith(OutputCaptureExtension.class)
class CosIdJdbcDisabledHintAutoConfigurationTest {
    
    static class Order {
        @Id
        @CosId
        Long id;
    }
    
    static class SpringId {
        @Id
        Long id;
    }
    
    static class NoId {
        String name;
    }
    
    private static ApplicationContextRunner runner(Class<?> entity) {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CosIdJdbcDisabledHintAutoConfiguration.class))
            .withBean(RelationalMappingContext.class, () -> {
                RelationalMappingContext mappingContext = new RelationalMappingContext();
                mappingContext.getRequiredPersistentEntity(entity);
                return mappingContext;
            });
    }
    
    @Test
    void namesEntitiesWithCosIdFields(CapturedOutput output) {
        runner(Order.class).run(context -> assertThat(output)
            .contains("have @Id or @CosId fields that CosId no longer fills")
            .contains(Order.class.getName()));
    }
    
    @Test
    void namesEntitiesWithASpringDataId(CapturedOutput output) {
        runner(SpringId.class).run(context -> assertThat(output)
            .contains("have @Id or @CosId fields that CosId no longer fills")
            .contains(SpringId.class.getName()));
    }
    
    @Test
    void staysQuietWithoutIdFields(CapturedOutput output) {
        runner(NoId.class).run(context -> assertThat(output).doesNotContain("CosId no longer fills"));
    }
    
    @Test
    void staysQuietWhenTheIntegrationIsOn(CapturedOutput output) {
        runner(Order.class).withPropertyValues(CosIdJdbcAutoConfiguration.ENABLED_KEY + "=true")
            .run(context -> assertThat(context).doesNotHaveBean(CosIdJdbcDisabledHintAutoConfiguration.class));
    }
}
