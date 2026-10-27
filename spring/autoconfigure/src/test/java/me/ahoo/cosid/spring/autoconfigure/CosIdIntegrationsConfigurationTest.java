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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.activiti.ActivitiIdGenerator;
import me.ahoo.cosid.axon.CosIdIdentifierFactory;
import me.ahoo.cosid.flowable.FlowableIdGenerator;
import me.ahoo.cosid.jackson.AsString;
import me.ahoo.cosid.jackson.CosIdJacksonModule;
import me.ahoo.cosid.mybatis.CosIdInterceptor;
import me.ahoo.cosid.spring.data.jdbc.CosIdBeforeConvertCallback;

import org.axonframework.common.IdentifierFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import tools.jackson.databind.json.JsonMapper;

/** {@link CosIdIntegrationsConfiguration}: each integration on its classes and properties, the Axon binding lifecycle. */
class CosIdIntegrationsConfigurationTest {
    static final ApplicationContextRunner RUNNER = CosIdAutoConfigurationTest.withStores()
        .withConfiguration(AutoConfigurations.of(CosIdIntegrationsConfiguration.class))
        .withPropertyValues(CosIdAutoConfigurationTest.MIXED);

    public static class Order {
        @AsString(generator = "order")
        public long id;
    }

    @Test
    void jacksonModuleOfTheRuntime() {
        RUNNER.run(context -> {
            assertNull(context.getStartupFailure());
            JsonMapper mapper = JsonMapper.builder().addModule(context.getBean(CosIdJacksonModule.class)).build();
            Order order = new Order();
            order.id = 5;
            assertThat(mapper.writeValueAsString(order), startsWith("{\"id\":\"O"));
        });
    }

    @Test
    void mybatisInterceptorWithItsListKey() {
        RUNNER.withPropertyValues("cosid.mybatis.list-key=orders").run(context ->
            assertThat(context.getBean(CosIdInterceptor.class).listKey(), is("orders")));
        RUNNER.run(context -> assertThat(context.getBean(CosIdInterceptor.class).listKey(), is(CosIdInterceptor.DEFAULT_LIST_KEY)));
        RUNNER.withPropertyValues("cosid.mybatis.enabled=false").run(context ->
            assertThat(context.getBeanNamesForType(CosIdInterceptor.class).length, is(0)));
        RUNNER.withClassLoader(new FilteredClassLoader("org.apache.ibatis")).run(context ->
            assertThat(context.getBeanNamesForType(CosIdInterceptor.class).length, is(0)));
    }

    @Test
    void dataJdbcCallbackOnlyWhenEnabled() {
        RUNNER.withBean(RelationalMappingContext.class, RelationalMappingContext::new).run(context ->
            assertThat(context.getBeanNamesForType(CosIdBeforeConvertCallback.class).length, is(0)));
        RUNNER.withBean(RelationalMappingContext.class, RelationalMappingContext::new)
            .withPropertyValues("cosid.jdbc.enabled=true", "cosid.jdbc.id-generator=order").run(context -> {
                assertNull(context.getStartupFailure());
                assertThat(context.getBeanNamesForType(CosIdBeforeConvertCallback.class).length, is(1));
            });
    }

    @Test
    void activitiIdGenerator() {
        RUNNER.withPropertyValues("cosid.activiti.generator-name=order").run(context -> {
            org.activiti.spring.SpringProcessEngineConfiguration engine = new org.activiti.spring.SpringProcessEngineConfiguration();
            context.getBean(org.activiti.spring.boot.ProcessEngineConfigurationConfigurer.class).configure(engine);
            assertThat(engine.getIdGenerator(), instanceOf(ActivitiIdGenerator.class));
            assertThat(engine.getIdGenerator().getNextId(), startsWith("O"));
        });
        RUNNER.withPropertyValues("cosid.activiti.enabled=false").run(context ->
            assertThat(context.getBeanNamesForType(org.activiti.spring.boot.ProcessEngineConfigurationConfigurer.class).length, is(0)));
    }

    @Test
    void flowableIdGenerator() {
        RUNNER.run(context -> {
            org.flowable.spring.SpringProcessEngineConfiguration engine = new org.flowable.spring.SpringProcessEngineConfiguration();
            @SuppressWarnings("unchecked")
            org.flowable.spring.boot.EngineConfigurationConfigurer<org.flowable.spring.SpringProcessEngineConfiguration> configurer =
                context.getBean(org.flowable.spring.boot.EngineConfigurationConfigurer.class);
            configurer.configure(engine);
            assertThat(engine.getIdGenerator(), instanceOf(FlowableIdGenerator.class));
            assertThat(((FlowableIdGenerator) engine.getIdGenerator()).generator(), is("default"));
            assertThat(engine.getIdGenerator().getNextId(), matchesPattern("\\d+"));
        });
        RUNNER.withPropertyValues("cosid.flowable.enabled=false").run(context ->
            assertThat(context.getBeanNamesForType(org.flowable.spring.boot.EngineConfigurationConfigurer.class).length, is(0)));
    }

    @Test
    void flowableIdGeneratorOnTheSpringProperty() {
        RUNNER.withPropertyValues("cosid.flowable.generator-name=order").run(context -> {
            assertNull(context.getStartupFailure());
            FlowableIdGenerator generator = (FlowableIdGenerator) flowableEngine(context).getIdGenerator();
            assertThat(generator.generator(), is("order"));
            assertThat(generator.getNextId(), startsWith("O"));
        });
    }

    @Test
    void springPropertyWinsOverTheSystemPropertyForWorkflowEngines() {
        System.setProperty(ActivitiIdGenerator.ID_KEY, "order");
        System.setProperty(FlowableIdGenerator.ID_KEY, "order");
        try {
            // Without the Spring property, the system property names the generator.
            RUNNER.run(context -> {
                assertNull(context.getStartupFailure());
                ActivitiIdGenerator activiti = (ActivitiIdGenerator) activitiEngine(context).getIdGenerator();
                assertThat(activiti.generator(), is("order"));
                assertThat(activiti.getNextId(), startsWith("O"));
                FlowableIdGenerator flowable = (FlowableIdGenerator) flowableEngine(context).getIdGenerator();
                assertThat(flowable.generator(), is("order"));
                assertThat(flowable.getNextId(), startsWith("O"));
            });
            // The Spring property wins over it.
            RUNNER.withPropertyValues("cosid.activiti.generator-name=default", "cosid.flowable.generator-name=default").run(context -> {
                assertNull(context.getStartupFailure());
                ActivitiIdGenerator activiti = (ActivitiIdGenerator) activitiEngine(context).getIdGenerator();
                assertThat(activiti.generator(), is("default"));
                assertThat(activiti.getNextId(), matchesPattern("\\d+"));
                FlowableIdGenerator flowable = (FlowableIdGenerator) flowableEngine(context).getIdGenerator();
                assertThat(flowable.generator(), is("default"));
                assertThat(flowable.getNextId(), matchesPattern("\\d+"));
            });
        } finally {
            System.clearProperty(ActivitiIdGenerator.ID_KEY);
            System.clearProperty(FlowableIdGenerator.ID_KEY);
        }
    }

    @Test
    void springPropertyWinsOverTheSystemPropertyForAxon() {
        IdentifierFactory axon = IdentifierFactory.getInstance();
        System.setProperty(CosIdIdentifierFactory.ID_KEY, "order");
        try {
            RUNNER.run(context -> {
                assertNull(context.getStartupFailure());
                assertThat("without the Spring property, the system property names the generator", axon.generateIdentifier(), startsWith("O"));
            });
            RUNNER.withPropertyValues("cosid.axon.generator-name=bill").run(context -> {
                assertNull(context.getStartupFailure());
                assertThat("the Spring property wins", axon.generateIdentifier(), matchesPattern("\\d{14}"));
            });
        } finally {
            System.clearProperty(CosIdIdentifierFactory.ID_KEY);
        }
        // Both contexts closed: nothing is bound.
        assertThrows(IllegalStateException.class, axon::generateIdentifier);
    }

    private static org.activiti.spring.SpringProcessEngineConfiguration activitiEngine(org.springframework.context.ApplicationContext context) {
        org.activiti.spring.SpringProcessEngineConfiguration engine = new org.activiti.spring.SpringProcessEngineConfiguration();
        context.getBean(org.activiti.spring.boot.ProcessEngineConfigurationConfigurer.class).configure(engine);
        return engine;
    }

    @SuppressWarnings("unchecked")
    private static org.flowable.spring.SpringProcessEngineConfiguration flowableEngine(org.springframework.context.ApplicationContext context) {
        org.flowable.spring.SpringProcessEngineConfiguration engine = new org.flowable.spring.SpringProcessEngineConfiguration();
        context.getBean(org.flowable.spring.boot.EngineConfigurationConfigurer.class).configure(engine);
        return engine;
    }

    @Test
    void axonFollowsEachContextsRuntime() {
        IdentifierFactory axon = IdentifierFactory.getInstance();
        assertThat(axon, instanceOf(CosIdIdentifierFactory.class));
        RUNNER.withPropertyValues("cosid.axon.generator-name=order").run(first -> {
            assertNull(first.getStartupFailure());
            assertThat(axon.generateIdentifier(), startsWith("O"));
            RUNNER.withPropertyValues("cosid.axon.generator-name=bill").run(second ->
                assertThat(axon.generateIdentifier(), matchesPattern("\\d{14}")));
            // The second context closed: back to the first one's runtime.
            assertThat(axon.generateIdentifier(), startsWith("O"));
        });
        // Both contexts closed: nothing is bound.
        assertThrows(IllegalStateException.class, axon::generateIdentifier);
    }

    @Test
    void nothingWhenCosIdIsOffOrTheModuleIsMissing() {
        RUNNER.withPropertyValues("cosid.enabled=false").run(context -> {
            assertThat(context.getBeanNamesForType(CosIdJacksonModule.class).length, is(0));
            assertThat(context.getBeanNamesForType(CosIdInterceptor.class).length, is(0));
        });
        RUNNER.withClassLoader(new FilteredClassLoader("me.ahoo.cosid.jackson", "me.ahoo.cosid.axon")).run(context -> {
            assertNull(context.getStartupFailure());
            assertThat(context.getBeanNamesForType(CosIdJacksonModule.class).length, is(0));
            assertThat(context.getBeanNamesForType(CosIdInterceptor.class).length, is(1));
        });
    }
}
