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

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.GuardDistribute;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.snowflake.SnowflakeId;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.util.function.SingletonSupplier;

import java.util.function.Supplier;

/**
 * Registers a bean definition per snowflake id of {@code cosid.snowflake.share} and {@code cosid.snowflake.provider},
 * named {@code {name}SnowflakeId}: other beans can depend on them ({@code @DependsOn("orderSnowflakeId")}).
 *
 * <p>The properties are bound from the environment and passed through every {@link CustomizeSnowflakeIdProperties} when
 * the bean definitions are registered, before other beans are created: a customizer must not depend on other beans.
 * The properties bean is that customized instance.
 * The ids themselves are created with the singletons, each distributing its machine id.</p>
 *
 * @since 4.0.0
 */
public class SnowflakeIdBeanDefinitionRegistrar implements BeanDefinitionRegistryPostProcessor, BeanPostProcessor, EnvironmentAware {
    private Environment environment;
    @Nullable
    private SnowflakeIdProperties customized;
    
    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
    
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        ConfigurableListableBeanFactory beanFactory = (ConfigurableListableBeanFactory) registry;
        SnowflakeIdProperties snowflakeIdProperties = Binder.get(environment).bindOrCreate(SnowflakeIdProperties.PREFIX, SnowflakeIdProperties.class);
        customized = snowflakeIdProperties;
        beanFactory.getBeanProvider(CustomizeSnowflakeIdProperties.class).orderedStream()
            .forEach(customize -> customize.customize(snowflakeIdProperties));
        Supplier<SnowflakeIdBeanRegistrar> registrar = SingletonSupplier.of(() -> new SnowflakeIdBeanRegistrar(beanFactory.getBean(CosIdProperties.class),
            beanFactory.getBean(MachineProperties.class), snowflakeIdProperties, beanFactory.getBean(InstanceId.class),
            beanFactory.getBean(IdGeneratorProvider.class), beanFactory.getBean(GuardDistribute.class), beanFactory.getBean(ClockBackwardsSynchronizer.class)));
        SnowflakeIdProperties.ShardIdDefinition share = snowflakeIdProperties.getShare();
        if (share.isEnabled()) {
            register(registry, IdGeneratorProvider.SHARE, share, registrar);
        }
        snowflakeIdProperties.getProvider().forEach((name, idDefinition) -> register(registry, name, idDefinition, registrar));
    }
    
    private static void register(BeanDefinitionRegistry registry, String name, SnowflakeIdProperties.IdDefinition idDefinition,
                                 Supplier<SnowflakeIdBeanRegistrar> registrar) {
        RootBeanDefinition beanDefinition = new RootBeanDefinition(SnowflakeId.class);
        beanDefinition.setInstanceSupplier(() -> registrar.get().create(name, idDefinition));
        registry.registerBeanDefinition(name + "SnowflakeId", beanDefinition);
    }
    
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // The bean definitions are all registered above.
    }
    
    /**
     * The {@code SnowflakeIdProperties} bean is the customized instance the ids were registered from, so the customizers run once.
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof SnowflakeIdProperties && customized != null) {
            return customized;
        }
        return bean;
    }
}
