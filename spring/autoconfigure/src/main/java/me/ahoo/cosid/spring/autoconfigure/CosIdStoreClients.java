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

import me.ahoo.cosid.runtime.RuntimeSpec;
import me.ahoo.cosid.runtime.StoreSpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The clients the context already has, by store name, passed to the stores as {@code StoreConfig.client}: the bean
 * named by {@code cosid.stores.<name>.client}, otherwise the single (or primary) bean of the store type's client type.
 * The runtime is registered as depending on each, so it closes before them.
 */
final class CosIdStoreClients {
    private static final Logger log = LoggerFactory.getLogger(CosIdStoreClients.class);
    private static final String REDIS_FACTORY = "org.springframework.data.redis.connection.RedisConnectionFactory";

    /** Store type → the client types looked up, in order. */
    private static final Map<String, List<String>> CLIENT_TYPES = Map.of(
        "jdbc", List.of("javax.sql.DataSource"),
        "redis", List.of("io.lettuce.core.AbstractRedisClient", REDIS_FACTORY),
        "mongo", List.of("com.mongodb.client.MongoClient"),
        "zookeeper", List.of("org.apache.curator.framework.CuratorFramework"));

    private CosIdStoreClients() {
    }

    static Map<String, Object> resolve(RuntimeSpec spec, CosIdProperties properties, ConfigurableListableBeanFactory beanFactory,
                                       String dependent) {
        Map<String, Object> clients = new LinkedHashMap<>();
        ClassLoader classLoader = beanFactory.getBeanClassLoader();
        spec.stores().forEach((store, storeSpec) -> {
            CosIdProperties.Store declared = properties.stores().get(store);
            Optional<String> bean = declared == null || declared.client() == null || declared.client().isBlank()
                ? find(storeSpec.type(), beanFactory, classLoader) : Optional.of(declared.client());
            bean.ifPresent(name -> {
                Object client = adapt(beanFactory.getBean(name), storeSpec, classLoader);
                beanFactory.registerDependentBean(name, dependent);
                clients.put(store, client);
                log.debug("Store [{}] uses the bean [{}]", store, name);
            });
        });
        return clients;
    }

    private static Optional<String> find(String type, ConfigurableListableBeanFactory beanFactory, ClassLoader classLoader) {
        for (String className : CLIENT_TYPES.getOrDefault(type, List.of())) {
            if (!ClassUtils.isPresent(className, classLoader)) {
                continue;
            }
            Class<?> clientType = ClassUtils.resolveClassName(className, classLoader);
            List<String> names = new ArrayList<>(List.of(beanFactory.getBeanNamesForType(clientType)));
            if (names.size() > 1) {
                names.removeIf(name -> !beanFactory.containsBeanDefinition(name) || !beanFactory.getBeanDefinition(name).isPrimary());
                if (names.size() != 1) {
                    log.info("Several {} beans for {} stores and none primary: set cosid.stores.<name>.client", className, type);
                    return Optional.empty();
                }
            }
            if (names.size() == 1) {
                return Optional.of(names.getFirst());
            }
        }
        return Optional.empty();
    }

    private static Object adapt(Object client, StoreSpec store, ClassLoader classLoader) {
        if ("redis".equals(store.type()) && ClassUtils.isPresent(REDIS_FACTORY, classLoader)
            && ClassUtils.isPresent("me.ahoo.cosid.store.redis.RedisScripts", classLoader)) {
            return RedisFactories.clientOf(client, store);
        }
        return client;
    }

    /** Loads the Spring Data Redis types only once they are known to be present. */
    private static final class RedisFactories {
        static Object clientOf(Object client, StoreSpec store) {
            if (client instanceof org.springframework.data.redis.connection.RedisConnectionFactory factory) {
                return SpringDataRedisScripts.clientOf(factory, store);
            }
            return client;
        }
    }
}
