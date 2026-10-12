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

import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.segment.SegmentId;
import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.util.function.SingletonSupplier;

import java.util.function.Supplier;

/**
 * Registers a bean definition per segment id of {@code cosid.segment.share} and {@code cosid.segment.provider}, named
 * {@code {name}SegmentId}: other beans can depend on them ({@code @DependsOn("orderSegmentId")}), and the context
 * closes them on shutdown, which stops a segment chain's background prefetch.
 *
 * <p>The properties are bound from the environment and passed through every {@link CustomizeSegmentIdProperties} when
 * the bean definitions are registered, before other beans are created: a customizer must not depend on other beans.
 * The properties bean is that customized instance.
 * The ids themselves are created with the singletons, on the {@link IdSegmentDistributorFactory} bean.</p>
 *
 * @since 4.0.0
 */
public class SegmentIdBeanDefinitionRegistrar implements BeanDefinitionRegistryPostProcessor, BeanPostProcessor, EnvironmentAware {
    private Environment environment;
    @Nullable
    private SegmentIdProperties customized;
    
    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
    
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        ConfigurableListableBeanFactory beanFactory = (ConfigurableListableBeanFactory) registry;
        SegmentIdProperties segmentIdProperties = Binder.get(environment).bindOrCreate(SegmentIdProperties.PREFIX, SegmentIdProperties.class);
        customized = segmentIdProperties;
        beanFactory.getBeanProvider(CustomizeSegmentIdProperties.class).orderedStream()
            .forEach(customize -> customize.customize(segmentIdProperties));
        Supplier<SegmentIdBeanRegistrar> registrar = SingletonSupplier.of(() -> new SegmentIdBeanRegistrar(beanFactory.getBean(CosIdProperties.class),
            segmentIdProperties, beanFactory.getBean(IdSegmentDistributorFactory.class), beanFactory.getBean(IdGeneratorProvider.class),
            beanFactory.getBean(PrefetchWorkerExecutorService.class)));
        SegmentIdProperties.ShardIdDefinition share = segmentIdProperties.getShare();
        if (share.isEnabled()) {
            register(registry, IdGeneratorProvider.SHARE, share, registrar);
        }
        segmentIdProperties.getProvider().forEach((name, idDefinition) -> register(registry, name, idDefinition, registrar));
    }
    
    private static void register(BeanDefinitionRegistry registry, String name, SegmentIdProperties.IdDefinition idDefinition,
                                 Supplier<SegmentIdBeanRegistrar> registrar) {
        RootBeanDefinition beanDefinition = new RootBeanDefinition(SegmentId.class);
        beanDefinition.setInstanceSupplier(() -> registrar.get().create(name, idDefinition));
        beanDefinition.setDestroyMethodName(AbstractBeanDefinition.INFER_METHOD);
        registry.registerBeanDefinition(name + "SegmentId", beanDefinition);
    }
    
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // The bean definitions are all registered above.
    }
    
    /**
     * The {@code SegmentIdProperties} bean is the customized instance the ids were registered from, so the customizers run once.
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof SegmentIdProperties && customized != null) {
            return customized;
        }
        return bean;
    }
}
