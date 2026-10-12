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

package me.ahoo.cosid.spring.boot.starter.segment;

import static org.assertj.core.api.Assertions.assertThat;

import me.ahoo.cosid.Decorator;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.segment.SegmentId;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.snowflake.SnowflakeIdBeanDefinitionRegistrar;
import me.ahoo.cosid.spring.boot.starter.snowflake.SnowflakeIdProperties;
import me.ahoo.cosid.test.segment.AtomicIdSegmentDistributor;

import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.Test;
import org.springframework.aot.generate.ClassNameGenerator;
import org.springframework.aot.generate.DefaultGenerationContext;
import org.springframework.aot.generate.GeneratedFiles;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.javapoet.ClassName;
import org.springframework.mock.env.MockEnvironment;

import java.util.concurrent.atomic.AtomicReference;

class SegmentIdBeanDefinitionRegistrarTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdAutoConfiguration.class, CosIdSegmentAutoConfiguration.class))
        .withBean(IdSegmentDistributorFactory.class, () -> definition -> new AtomicIdSegmentDistributor())
        .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true", "cosid.provider.isolated=true",
            SegmentIdProperties.PREFIX + ".share.enabled=false", SegmentIdProperties.PREFIX + ".provider.order.step=10");
    
    @Test
    void segmentIdsAreBeanDefinitionsThatOtherBeansCanDependOn() {
        contextRunner.withUserConfiguration(DependsOnOrder.class).run(context -> {
            assertThat(context.getBeanFactory().containsBeanDefinition("orderSegmentId")).isTrue();
            SegmentId order = context.getBean("orderSegmentId", SegmentId.class);
            assertThat(context.getBean(IdGeneratorProvider.class).getRequired("order")).isSameAs(order);
            assertThat(context.getBean(DependsOnOrder.class).seen.get()).isSameAs(order);
        });
    }
    
    @Test
    void closingTheContextClosesTheSegmentChain() {
        AtomicReference<SegmentChainId> chain = new AtomicReference<>();
        contextRunner.run(context -> {
            SegmentId order = context.getBean("orderSegmentId", SegmentId.class);
            chain.set((SegmentChainId) Decorator.getActual(order));
            assertThat(chain.get().isClosed()).isFalse();
        });
        assertThat(chain.get().isClosed()).isTrue();
    }
    
    @Test
    void customizerRunsOnThePropertiesBeanAndItsAddedIdsAreOnlyInTheProvider() {
        contextRunner
            .withBean(CustomizeSegmentIdProperties.class, () -> properties -> properties.getProvider().put("added", new SegmentIdProperties.IdDefinition()))
            .run(context -> {
                assertThat(context).hasBean("orderSegmentId").doesNotHaveBean("addedSegmentId");
                assertThat(context.getBean(SegmentIdProperties.class).getProvider()).containsKeys("order", "added");
                assertThat(context.getBean(IdGeneratorProvider.class).get("added")).isPresent();
            });
    }
    
    @Test
    void configurationDeclaringACustomizerKeepsItsInjectionAndLifecycle() {
        contextRunner.withPropertyValues("test.greeting=hello").withUserConfiguration(MainConfiguration.class).run(context -> {
            MainConfiguration configuration = context.getBean(MainConfiguration.class);
            assertThat(configuration.greeting).isEqualTo("hello");
            assertThat(configuration.idGeneratorProvider).isNotNull();
            assertThat(configuration.postConstructed).isTrue();
            assertThat(context.getBean(SegmentIdProperties.class).getProvider()).containsKey("customized");
        });
    }
    
    @Test
    void aSecondContextOnTheSharedProviderGetsItsOwnOpenIds() {
        ApplicationContextRunner shared = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CosIdAutoConfiguration.class, CosIdSegmentAutoConfiguration.class))
            .withBean(IdSegmentDistributorFactory.class, () -> definition -> new AtomicIdSegmentDistributor())
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true", SegmentIdProperties.PREFIX + ".share.enabled=false",
                SegmentIdProperties.PREFIX + ".provider.restart.step=10");
        AtomicReference<SegmentId> first = new AtomicReference<>();
        shared.run(context -> first.set(context.getBean("restartSegmentId", SegmentId.class)));
        assertThat(DefaultIdGeneratorProvider.INSTANCE.get("restart")).isEmpty();
        
        shared.run(context -> {
            SegmentId second = context.getBean("restartSegmentId", SegmentId.class);
            assertThat(second).isNotSameAs(first.get());
            assertThat(DefaultIdGeneratorProvider.INSTANCE.getRequired("restart")).isSameAs(second);
            assertThat(((SegmentChainId) Decorator.getActual(second)).isClosed()).isFalse();
        });
        assertThat(DefaultIdGeneratorProvider.INSTANCE.get("restart")).isEmpty();
    }
    
    @Test
    void aheadOfTimeProcessingSucceeds() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true", "cosid.provider.isolated=true",
                SegmentIdProperties.PREFIX + ".share.enabled=false", SegmentIdProperties.PREFIX + ".provider.order.step=10").applyTo(context);
            context.register(CosIdAutoConfiguration.class, CosIdSegmentAutoConfiguration.class, MainConfiguration.class, DistributorConfiguration.class);
            InMemoryGeneratedFiles files = new InMemoryGeneratedFiles();
            DefaultGenerationContext generationContext = new DefaultGenerationContext(new ClassNameGenerator(ClassName.get(getClass())), files);
            new ApplicationContextAotGenerator().processAheadOfTime(context, generationContext);
            generationContext.writeGeneratedContent();
            assertThat(files.getGeneratedFiles(GeneratedFiles.Kind.SOURCE)).isNotEmpty();
            assertThat(context.getBeanFactory().getBeanDefinition("orderSegmentId").getFactoryMethodName()).isEqualTo("createBean");
        }
    }
    
    @Test
    void runningTheRegistrarsAgainKeepsTheGeneratedDefinitions() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registry.setAllowBeanDefinitionOverriding(false);
        MockEnvironment environment = new MockEnvironment()
            .withProperty(SegmentIdProperties.PREFIX + ".provider.order.step", "10")
            .withProperty(SnowflakeIdProperties.PREFIX + ".provider.order.epoch", "1577203200000");
        SegmentIdBeanDefinitionRegistrar segment = new SegmentIdBeanDefinitionRegistrar();
        segment.setEnvironment(environment);
        SnowflakeIdBeanDefinitionRegistrar snowflake = new SnowflakeIdBeanDefinitionRegistrar();
        snowflake.setEnvironment(environment);
        for (int i = 0; i < 2; i++) {
            segment.postProcessBeanDefinitionRegistry(registry);
            snowflake.postProcessBeanDefinitionRegistry(registry);
        }
        assertThat(registry.getBeanDefinitionNames()).containsExactlyInAnyOrder("__share__SegmentId", "orderSegmentId", "__share__SnowflakeId", "orderSnowflakeId");
    }
    
    @Configuration(proxyBeanMethods = false)
    static class DistributorConfiguration {
        @Bean
        IdSegmentDistributorFactory idSegmentDistributorFactory() {
            return definition -> new AtomicIdSegmentDistributor();
        }
    }
    
    @Configuration(proxyBeanMethods = false)
    static class MainConfiguration {
        @Value("${test.greeting}")
        String greeting;
        @Autowired
        IdGeneratorProvider idGeneratorProvider;
        boolean postConstructed;
        
        @PostConstruct
        void init() {
            postConstructed = true;
        }
        
        @Bean
        CustomizeSegmentIdProperties customizeSegmentIdProperties() {
            return properties -> properties.getProvider().put("customized", new SegmentIdProperties.IdDefinition());
        }
    }
    
    @Configuration(proxyBeanMethods = false)
    static class DependsOnOrder {
        final AtomicReference<Object> seen = new AtomicReference<>();
        
        @Bean
        @DependsOn("orderSegmentId")
        Object readsTheProvider(IdGeneratorProvider idGeneratorProvider) {
            seen.set(idGeneratorProvider.getRequired("order"));
            return new Object();
        }
    }
}
