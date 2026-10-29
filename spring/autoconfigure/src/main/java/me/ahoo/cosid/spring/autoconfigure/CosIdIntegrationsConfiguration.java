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

import me.ahoo.cosid.accessor.IdFiller;
import me.ahoo.cosid.activiti.ActivitiIdGenerator;
import me.ahoo.cosid.axon.CosIdIdentifierFactory;
import me.ahoo.cosid.flowable.FlowableIdGenerator;
import me.ahoo.cosid.jackson.CosIdJacksonModule;
import me.ahoo.cosid.mybatis.CosIdInterceptor;
import me.ahoo.cosid.runtime.CosIdRuntime;
import me.ahoo.cosid.spring.data.jdbc.CosIdBeforeConvertCallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The integrations (07-runtime-and-spring.md §5, D29), each when its module ({@code cosid-jackson}, {@code cosid-mybatis},
 * {@code cosid-spring-data-jdbc}, {@code cosid-activiti}, {@code cosid-flowable}, {@code cosid-axon}) and the classes it
 * plugs into are on the classpath:
 * <ul>
 *   <li>Jackson: a {@link CosIdJacksonModule} of the runtime, so {@code @AsString(generator = ...)} works.</li>
 *   <li>MyBatis ({@code cosid.mybatis.enabled}, default on): the {@link CosIdInterceptor}, batch inserts under
 *   {@code cosid.mybatis.list-key} (default {@code list}).</li>
 *   <li>Spring Data JDBC ({@code cosid.jdbc.enabled}, off by default as in 4.x): the {@link CosIdBeforeConvertCallback},
 *   {@code @Id} properties from {@code cosid.jdbc.id-generator} (default {@code default}).</li>
 *   <li>Activiti and Flowable ({@code cosid.activiti.enabled}, {@code cosid.flowable.enabled}, default on): the
 *   engine's id generator on {@code cosid.activiti.generator-name} / {@code cosid.flowable.generator-name}, else the
 *   system property {@code cosid.activiti} / {@code cosid.flowable}, else {@code default}.</li>
 *   <li>Axon: binds the runtime to {@link CosIdIdentifierFactory} once it is started and unbinds it before it is closed,
 *   generator {@code cosid.axon.generator-name}, else the system property {@code cosid.axon}, else {@code default}.</li>
 * </ul>
 */
@AutoConfiguration(after = CosIdAutoConfiguration.class)
@ConditionalOnProperty(prefix = CosIdProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class CosIdIntegrationsConfiguration {
    static final String MYBATIS_ENABLED = "cosid.mybatis.enabled";
    static final String MYBATIS_LIST_KEY = "cosid.mybatis.list-key";
    static final String JDBC_ENABLED = "cosid.jdbc.enabled";
    static final String JDBC_ID_GENERATOR = "cosid.jdbc.id-generator";
    static final String ACTIVITI_ENABLED = "cosid.activiti.enabled";
    static final String ACTIVITI_GENERATOR = "cosid.activiti.generator-name";
    static final String FLOWABLE_ENABLED = "cosid.flowable.enabled";
    static final String FLOWABLE_GENERATOR = "cosid.flowable.generator-name";
    static final String AXON_GENERATOR = "cosid.axon.generator-name";
    private static final Logger log = LoggerFactory.getLogger(CosIdIntegrationsConfiguration.class);

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"me.ahoo.cosid.jackson.CosIdJacksonModule", "tools.jackson.databind.JacksonModule"})
    static class Jackson {
        @Bean
        @ConditionalOnMissingBean
        CosIdJacksonModule cosIdJacksonModule(CosIdRuntime runtime) {
            return new CosIdJacksonModule(runtime);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"me.ahoo.cosid.mybatis.CosIdInterceptor", "org.apache.ibatis.plugin.Interceptor"})
    @ConditionalOnProperty(name = MYBATIS_ENABLED, havingValue = "true", matchIfMissing = true)
    static class Mybatis {
        @Bean
        @ConditionalOnMissingBean
        CosIdInterceptor cosIdInterceptor(IdFiller filler, Environment environment) {
            return new CosIdInterceptor(filler, environment.getProperty(MYBATIS_LIST_KEY, CosIdInterceptor.DEFAULT_LIST_KEY));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"me.ahoo.cosid.spring.data.jdbc.CosIdBeforeConvertCallback",
        "org.springframework.data.relational.core.mapping.event.BeforeConvertCallback"})
    @ConditionalOnProperty(name = JDBC_ENABLED, havingValue = "true")
    static class DataJdbc {
        @Bean
        @ConditionalOnMissingBean
        CosIdBeforeConvertCallback cosIdBeforeConvertCallback(CosIdRuntime runtime, RelationalMappingContext mappingContext,
                                                              Environment environment) {
            return new CosIdBeforeConvertCallback(runtime, mappingContext,
                environment.getProperty(JDBC_ID_GENERATOR, CosIdBeforeConvertCallback.DEFAULT_ID_GENERATOR));
        }
    }

    /**
     * R1-int-02: cosid-spring-data-jdbc on the classpath does nothing until {@code cosid.jdbc.enabled=true} (it is off by
     * default, unlike the other integrations, since a callback on every converted entity is not free); say so once,
     * rather than leave {@code @CosId} fields empty without a word.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"me.ahoo.cosid.spring.data.jdbc.CosIdBeforeConvertCallback",
        "org.springframework.data.relational.core.mapping.event.BeforeConvertCallback"})
    @ConditionalOnProperty(name = JDBC_ENABLED, havingValue = "false", matchIfMissing = true)
    static class DataJdbcOff {
        @Bean
        SmartInitializingSingleton cosIdDataJdbcOffNotice() {
            return () -> log.info("cosid-spring-data-jdbc is on the classpath, but {} is not true: @CosId fields of Spring Data JDBC entities are"
                + " not filled; set {}=true to fill them", JDBC_ENABLED, JDBC_ENABLED);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"me.ahoo.cosid.activiti.ActivitiIdGenerator", "org.activiti.spring.boot.ProcessEngineConfigurationConfigurer"})
    @ConditionalOnProperty(name = ACTIVITI_ENABLED, havingValue = "true", matchIfMissing = true)
    static class Activiti {
        @Bean
        org.activiti.spring.boot.ProcessEngineConfigurationConfigurer cosIdActivitiConfigurer(CosIdRuntime runtime, Environment environment) {
            String generator = environment.getProperty(ACTIVITI_GENERATOR);
            return configuration -> configuration.setIdGenerator(new ActivitiIdGenerator(runtime, generator));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"me.ahoo.cosid.flowable.FlowableIdGenerator", "org.flowable.spring.boot.EngineConfigurationConfigurer"})
    @ConditionalOnProperty(name = FLOWABLE_ENABLED, havingValue = "true", matchIfMissing = true)
    static class Flowable {
        @Bean
        org.flowable.spring.boot.EngineConfigurationConfigurer<org.flowable.spring.SpringProcessEngineConfiguration> cosIdFlowableConfigurer(
            CosIdRuntime runtime, Environment environment) {
            String generator = environment.getProperty(FLOWABLE_GENERATOR);
            return configuration -> configuration.setIdGenerator(new FlowableIdGenerator(runtime, generator));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"me.ahoo.cosid.axon.CosIdIdentifierFactory", "org.axonframework.common.IdentifierFactory"})
    static class Axon {
        @Bean
        static AxonBinder cosIdAxonBinder() {
            return new AxonBinder();
        }
    }

    /**
     * Binds each {@link CosIdRuntime} bean to {@link CosIdIdentifierFactory} right after it is started, before any other
     * bean can use it, and unbinds it right before it is closed: the binding follows the runtime's lifecycle.
     */
    static final class AxonBinder implements DestructionAwareBeanPostProcessor, EnvironmentAware {
        private final Map<CosIdRuntime, CosIdIdentifierFactory.Binding> bindings = new IdentityHashMap<>();
        private String generator;

        @Override
        public void setEnvironment(Environment environment) {
            this.generator = environment.getProperty(AXON_GENERATOR);
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean instanceof CosIdRuntime runtime) {
                synchronized (bindings) {
                    bindings.computeIfAbsent(runtime, started -> CosIdIdentifierFactory.bind(started, generator));
                }
            }
            return bean;
        }

        @Override
        public void postProcessBeforeDestruction(Object bean, String beanName) {
            CosIdIdentifierFactory.Binding binding;
            synchronized (bindings) {
                binding = bindings.remove(bean);
            }
            if (binding != null) {
                binding.close();
            }
        }

        @Override
        public boolean requiresDestruction(Object bean) {
            return bean instanceof CosIdRuntime;
        }
    }
}
