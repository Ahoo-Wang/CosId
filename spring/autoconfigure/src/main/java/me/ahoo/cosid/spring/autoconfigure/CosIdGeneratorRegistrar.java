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

import me.ahoo.cosid.GeneratorDisabledException;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.IdGenerators;
import me.ahoo.cosid.runtime.CosIdRuntime;
import me.ahoo.cosid.runtime.GeneratorSpec;
import me.ahoo.cosid.runtime.RuntimeSpec;
import me.ahoo.cosid.segment.PeriodicSegmentGenerator;
import me.ahoo.cosid.time.TimeBasedGenerator;

import org.springframework.aot.AotDetector;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

/**
 * Registers one bean per generator, named after it, whose instance {@link CosIdRuntime} provides (07 §4.1): the runtime
 * stays the one source of truth. Only names and types are registered here, while the bean definitions are processed;
 * the spec is built then from {@code cosid.*} and the {@link RuntimeSpecCustomizer} beans, and is the
 * {@code cosIdRuntimeSpec} bean the runtime starts from.
 *
 * <p>Bean types: {@link IdGenerator} for {@code snowflake} and {@code segment} (the {@code default} one is primary),
 * {@link TimeBasedGenerator} for {@code cosid80}, {@link PeriodicSegmentGenerator} for {@code periodic-segment}. The
 * bean of a disabled {@code long} generator throws {@link GeneratorDisabledException} on each call; that of a disabled
 * {@code cosid80} or {@code periodic-segment} one is lazy and fails where it is injected, with that exception as the
 * cause. Every generator bean depends on the runtime, so it is destroyed before the runtime closes.
 */
public class CosIdGeneratorRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, BeanFactoryAware {
    public static final String RUNTIME_BEAN = "cosIdRuntime";
    public static final String SPEC_BEAN = "cosIdRuntimeSpec";

    private volatile Environment environment;
    private volatile ListableBeanFactory beanFactory;
    private volatile RuntimeSpec spec;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = (ListableBeanFactory) beanFactory;
    }

    /**
     * The spec: {@code cosid.*} mapped by {@link CosIdSpecs}, then customized. Built once.
     *
     * @return the spec
     */
    public synchronized RuntimeSpec spec() {
        if (spec == null) {
            CosIdProperties properties = Binder.get(environment).bindOrCreate(CosIdProperties.PREFIX, Bindable.of(CosIdProperties.class));
            RuntimeSpec built = CosIdSpecs.toSpec(properties, ZoneId.systemDefault());
            for (RuntimeSpecCustomizer customizer : beanFactory.getBeanProvider(RuntimeSpecCustomizer.class).orderedStream().toList()) {
                built = Objects.requireNonNull(customizer.customize(built), () -> customizer + " returned no spec");
            }
            spec = built;
        }
        return spec;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (AotDetector.useGeneratedArtifacts()) {
            // The definitions were registered at build time and are part of the generated code.
            return;
        }
        boolean counted = Boolean.parseBoolean(environment.getProperty(CosIdProperties.PREFIX + ".metrics.generated"));
        spec().generators().forEach((name, generator) -> {
            if (registry.containsBeanDefinition(name)) {
                throw new IllegalStateException("The bean name [" + name + "] of the CosId generator [" + name
                    + "] is taken by another bean: rename the generator or the bean");
            }
            registry.registerBeanDefinition(name, definition(name, generator, counted));
        });
    }

    static RootBeanDefinition definition(String name, GeneratorSpec spec, boolean counted) {
        final boolean disabled = spec instanceof GeneratorSpec.Disabled;
        GeneratorSpec actual = spec instanceof GeneratorSpec.Disabled(GeneratorSpec inner) ? inner : spec;
        Class<?> type = beanType(actual);
        RootBeanDefinition definition = new RootBeanDefinition(GeneratorBean.class);
        definition.getConstructorArgumentValues().addIndexedArgumentValue(0, new RuntimeBeanReference(RUNTIME_BEAN));
        definition.getConstructorArgumentValues().addIndexedArgumentValue(1, name);
        definition.getConstructorArgumentValues().addIndexedArgumentValue(2, type);
        definition.getConstructorArgumentValues().addIndexedArgumentValue(3, counted && type == IdGenerator.class);
        definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, type);
        definition.setDependsOn(RUNTIME_BEAN);
        definition.setRole(BeanDefinition.ROLE_APPLICATION);
        definition.setPrimary(RuntimeSpec.DEFAULT_GENERATOR.equals(name));
        definition.setLazyInit(disabled && type != IdGenerator.class);
        return definition;
    }

    static Class<?> beanType(GeneratorSpec spec) {
        return switch (spec) {
            case GeneratorSpec.Snowflake snowflake -> IdGenerator.class;
            case GeneratorSpec.Segment segment -> IdGenerator.class;
            case GeneratorSpec.CosId80 cosId80 -> TimeBasedGenerator.class;
            case GeneratorSpec.PeriodicSegment periodic -> PeriodicSegmentGenerator.class;
            case GeneratorSpec.Disabled disabled -> beanType(disabled.generator());
        };
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }

    /**
     * The bean of one generator, looked up in the runtime.
     *
     * @param <T> the bean type
     */
    public static final class GeneratorBean<T> implements FactoryBean<T> {
        private final CosIdRuntime runtime;
        private final String name;
        private final Class<T> type;
        private final boolean counted;

        /**
         * A generator bean.
         *
         * @param runtime the runtime
         * @param name    the generator name
         * @param type    the bean type
         * @param counted whether a {@code long} generator counts its ids ({@code cosid.metrics.generated})
         */
        public GeneratorBean(CosIdRuntime runtime, String name, Class<T> type, boolean counted) {
            this.runtime = runtime;
            this.name = name;
            this.type = type;
            this.counted = counted;
        }

        @Override
        public T getObject() {
            if (type != IdGenerator.class) {
                return runtime.get(name, type);
            }
            IdGenerator ids;
            try {
                ids = runtime.generator(name);
            } catch (GeneratorDisabledException e) {
                // D24: the bean exists, its calls throw.
                ids = IdGenerators.lazy(() -> runtime.generator(name));
            }
            return type.cast(counted ? new Counted(ids) : ids);
        }

        @Override
        public Class<T> getObjectType() {
            return type;
        }
    }

    /**
     * A {@code long} generator that counts its ids, the {@code cosid.generator.generated} metric.
     */
    public static final class Counted implements IdGenerator {
        private final IdGenerator ids;
        private final LongAdder generated = new LongAdder();

        Counted(IdGenerator ids) {
            this.ids = ids;
        }

        @Override
        public long generate() {
            long id = ids.generate();
            generated.increment();
            return id;
        }

        /**
         * The ids generated so far.
         *
         * @return the count
         */
        public long generated() {
            return generated.sum();
        }

        /**
         * The generator counted.
         *
         * @return the generator
         */
        public IdGenerator ids() {
            return ids;
        }
    }
}
