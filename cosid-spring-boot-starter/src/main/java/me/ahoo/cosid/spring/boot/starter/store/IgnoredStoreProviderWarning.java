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

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Warns at startup when the application defines a {@link CosIdStoreProvider} of the active store type but a built-in
 * backend bean was created instead, so the provider is ignored. That happens when the provider bean's type is not known
 * while the backends are registered: a raw-typed or programmatically registered {@code FactoryBean}, or a provider
 * defined by an auto-configuration ordered after CosId's backends.
 *
 * @author ahoo wang
 * @since 3.4.0
 */
@Slf4j
class IgnoredStoreProviderWarning implements SmartInitializingSingleton {
    private final ListableBeanFactory beanFactory;
    private final String side;
    private final Class<?> createdType;
    private final String storeBeanName;
    private final Supplier<String> storeType;
    
    IgnoredStoreProviderWarning(ListableBeanFactory beanFactory, String side, Class<?> createdType, String storeBeanName, Supplier<String> storeType) {
        this.beanFactory = beanFactory;
        this.side = side;
        this.createdType = createdType;
        this.storeBeanName = storeBeanName;
        this.storeType = storeType;
    }
    
    @Override
    public void afterSingletonsInstantiated() {
        List<String> created = Arrays.asList(beanFactory.getBeanNamesForType(createdType));
        if (created.isEmpty() || created.contains(storeBeanName)) {
            return;
        }
        String type = storeType.get();
        List<String> ignored = new ArrayList<>();
        for (Map.Entry<String, CosIdStoreProvider> entry : beanFactory.getBeansOfType(CosIdStoreProvider.class).entrySet()) {
            if (!(entry.getValue() instanceof BuiltInStoreProvider) && StoreTypes.same(type, entry.getValue().getType())) {
                ignored.add(entry.getKey());
            }
        }
        if (!ignored.isEmpty() && log.isWarnEnabled()) {
            log.warn("CosIdStoreProvider {} of type [{}] is ignored: the {} {} is used instead. Define the provider as a bean of its concrete type, "
                + "in a configuration processed before CosId's backend auto-configurations.", ignored, type, side, created);
        }
    }
}
