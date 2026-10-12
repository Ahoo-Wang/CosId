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

package me.ahoo.cosid.spring.boot.starter.store;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when the built-in backend beans should be created: the application defines no {@link CosIdStoreProvider} of
 * its own. Otherwise the store auto-configuration creates the distributor or factory with the provider of the selected
 * type.
 *
 * @author ahoo wang
 * @since 3.4.0
 */
abstract class OnBuiltInStoreCondition extends SpringBootCondition implements ConfigurationCondition {
    @Override
    public ConfigurationPhase getConfigurationPhase() {
        return ConfigurationPhase.REGISTER_BEAN;
    }
    
    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
        if (beanFactory != null) {
            for (String name : beanFactory.getBeanNamesForType(CosIdStoreProvider.class, true, false)) {
                Class<?> type = beanFactory.getType(name, false);
                if (type == null || !BuiltInStoreProvider.class.isAssignableFrom(type)) {
                    return ConditionOutcome.noMatch("the application defines CosIdStoreProvider " + name);
                }
            }
        }
        return ConditionOutcome.match("no application CosIdStoreProvider");
    }
    
    static final class Machine extends OnBuiltInStoreCondition {
    }
    
    static final class Segment extends OnBuiltInStoreCondition {
    }
}
