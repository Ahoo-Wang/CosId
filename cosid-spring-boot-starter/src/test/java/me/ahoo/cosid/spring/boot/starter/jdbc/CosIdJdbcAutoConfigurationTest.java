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
import static org.mockito.Mockito.mock;

import me.ahoo.cosid.accessor.parser.FieldDefinitionParser;
import me.ahoo.cosid.accessor.registry.CosIdAccessorRegistry;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;
import me.ahoo.cosid.spring.data.jdbc.CosIdBeforeConvertCallback;
import me.ahoo.cosid.spring.data.jdbc.IdAnnotationDefinitionParser;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CosIdJdbcAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdAutoConfiguration.class, CosIdJdbcAutoConfiguration.class))
        .withBean(CosIdAccessorRegistry.class, () -> mock(CosIdAccessorRegistry.class));

    @Test
    void createsJdbcFieldParserAndBeforeConvertCallbackWhenDataJdbcAdapterIsPresent() {
        this.contextRunner.run(context -> {
            assertThat(context)
                .hasSingleBean(IdAnnotationDefinitionParser.class)
                .hasSingleBean(CosIdBeforeConvertCallback.class);
            assertThat(context.getBean(IdAnnotationDefinitionParser.class))
                .isSameAs(IdAnnotationDefinitionParser.INSTANCE);
            assertThat(context.getBeansOfType(FieldDefinitionParser.class))
                .containsKey("idAnnotationDefinitionParser");
        });
    }

    @Test
    void backsOffWhenUserProvidesBeforeConvertCallback() {
        CosIdBeforeConvertCallback callback = mock(CosIdBeforeConvertCallback.class);

        this.contextRunner
            .withBean(CosIdBeforeConvertCallback.class, () -> callback)
            .run(context -> assertThat(context.getBean(CosIdBeforeConvertCallback.class)).isSameAs(callback));
    }

    @Test
    void doesNotCreateJdbcBeansWhenCosIdIsDisabled() {
        this.contextRunner
            .withPropertyValues("cosid.enabled=false")
            .run(context -> assertThat(context)
                .doesNotHaveBean(IdAnnotationDefinitionParser.class)
                .doesNotHaveBean(CosIdBeforeConvertCallback.class));
    }

    @Test
    void doesNotCreateJdbcBeansWhenDataJdbcCallbackClassIsMissing() {
        this.contextRunner
            .withClassLoader(new FilteredClassLoader(CosIdBeforeConvertCallback.class))
            .run(context -> assertThat(context)
                .doesNotHaveBean(IdAnnotationDefinitionParser.class)
                .doesNotHaveBean(CosIdBeforeConvertCallback.class));
    }
}
