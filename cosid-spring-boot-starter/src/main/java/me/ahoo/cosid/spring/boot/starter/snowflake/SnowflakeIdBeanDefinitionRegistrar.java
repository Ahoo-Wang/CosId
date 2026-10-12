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

package me.ahoo.cosid.spring.boot.starter.snowflake;

import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.snowflake.SnowflakeId;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * Registers a bean definition per snowflake id of {@code cosid.snowflake.share} and {@code cosid.snowflake.provider},
 * named {@code {name}SnowflakeId}: other beans can depend on them ({@code @DependsOn("orderSnowflakeId")}).
 *
 * <p>It only reads the id names from the environment and instantiates nothing. Each bean definition is the factory
 * method {@link SnowflakeIdBeanRegistrar#createBean(String)} of the {@link SnowflakeIdBeanRegistrar#BEAN_NAME} bean, which
 * runs the {@link CustomizeSnowflakeIdProperties} in the normal bean lifecycle. Ids a customizer adds have no bean
 * definition: they are only registered in the {@code IdGeneratorProvider}.</p>
 *
 * @since 4.0.0
 */
public class SnowflakeIdBeanDefinitionRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {
    private Environment environment;
    
    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
    
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        SnowflakeIdProperties snowflakeIdProperties = Binder.get(environment).bindOrCreate(SnowflakeIdProperties.PREFIX, SnowflakeIdProperties.class);
        if (snowflakeIdProperties.getShare().isEnabled()) {
            register(registry, IdGeneratorProvider.SHARE);
        }
        snowflakeIdProperties.getProvider().keySet().forEach(name -> register(registry, name));
    }
    
    private static void register(BeanDefinitionRegistry registry, String name) {
        String beanName = SnowflakeIdBeanRegistrar.beanName(name);
        if (registry.containsBeanDefinition(beanName)) {
            // Ahead-of-time processing already generated the definition, and runs this registrar again at runtime.
            return;
        }
        RootBeanDefinition beanDefinition = new RootBeanDefinition();
        beanDefinition.setTargetType(SnowflakeId.class);
        beanDefinition.setFactoryBeanName(SnowflakeIdBeanRegistrar.BEAN_NAME);
        beanDefinition.setFactoryMethodName("createBean");
        beanDefinition.getConstructorArgumentValues().addIndexedArgumentValue(0, name);
        registry.registerBeanDefinition(beanName, beanDefinition);
    }
    
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // The bean definitions are all registered above.
    }
}
