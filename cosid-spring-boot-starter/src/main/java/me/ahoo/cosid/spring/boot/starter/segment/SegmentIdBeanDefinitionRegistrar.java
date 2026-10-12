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
import me.ahoo.cosid.segment.SegmentId;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * Registers a bean definition per segment id of {@code cosid.segment.share} and {@code cosid.segment.provider}, named
 * {@code {name}SegmentId}: other beans can depend on them ({@code @DependsOn("orderSegmentId")}), and the context
 * closes them on shutdown, which stops a segment chain's background prefetch.
 *
 * <p>It only reads the id names from the environment and instantiates nothing. Each bean definition is the factory
 * method {@link SegmentIdBeanRegistrar#createBean(String)} of the {@link SegmentIdBeanRegistrar#BEAN_NAME} bean, which
 * runs the {@link CustomizeSegmentIdProperties} in the normal bean lifecycle. Ids a customizer adds have no bean
 * definition: they are only registered in the {@code IdGeneratorProvider}.</p>
 *
 * @since 4.0.0
 */
public class SegmentIdBeanDefinitionRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {
    private Environment environment;
    
    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
    
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        SegmentIdProperties segmentIdProperties = Binder.get(environment).bindOrCreate(SegmentIdProperties.PREFIX, SegmentIdProperties.class);
        if (segmentIdProperties.getShare().isEnabled()) {
            register(registry, IdGeneratorProvider.SHARE);
        }
        segmentIdProperties.getProvider().keySet().forEach(name -> register(registry, name));
    }
    
    private static void register(BeanDefinitionRegistry registry, String name) {
        RootBeanDefinition beanDefinition = new RootBeanDefinition();
        beanDefinition.setTargetType(SegmentId.class);
        beanDefinition.setFactoryBeanName(SegmentIdBeanRegistrar.BEAN_NAME);
        beanDefinition.setFactoryMethodName("createBean");
        beanDefinition.getConstructorArgumentValues().addIndexedArgumentValue(0, name);
        beanDefinition.setDestroyMethodName(AbstractBeanDefinition.INFER_METHOD);
        registry.registerBeanDefinition(SegmentIdBeanRegistrar.beanName(name), beanDefinition);
    }
    
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // The bean definitions are all registered above.
    }
}
