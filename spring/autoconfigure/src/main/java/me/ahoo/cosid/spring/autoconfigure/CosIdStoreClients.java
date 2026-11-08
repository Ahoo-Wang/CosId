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

import me.ahoo.cosid.runtime.GeneratorSpec;
import me.ahoo.cosid.runtime.MachineSpec;
import me.ahoo.cosid.runtime.RuntimeSpec;
import me.ahoo.cosid.runtime.StoreSpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.NamedBeanHolder;
import org.springframework.util.ClassUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The clients the context already has, by store name, passed to the stores as {@code StoreConfig.client}: the bean
 * named by {@code cosid.stores.<name>.client}, otherwise the single (or primary) bean of the store type's client type,
 * in this context or, when it has none, in an ancestor, as Spring injects one. Only the stores the runtime opens get
 * one: a store that only disabled generators, or nothing, refer to needs no client, so its bean is neither looked up
 * nor created. The runtime is registered as depending on each, so it closes before them.
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
        Set<String> inUse = storesInUse(spec);
        spec.stores().forEach((store, storeSpec) -> {
            if (!inUse.contains(store)) {
                // #1455: as the runtime opens only the stores in use, a disabled generator needs no client bean.
                log.debug("Store [{}] is used by no enabled generator or machine: no client looked up", store);
                return;
            }
            CosIdProperties.Store declared = properties.stores().get(store);
            Optional<NamedBeanHolder<?>> bean = declared == null || declared.client() == null || declared.client().isBlank()
                ? find(storeSpec.type(), beanFactory, classLoader)
                : Optional.of(new NamedBeanHolder<>(declared.client(), beanFactory.getBean(declared.client())));
            bean.ifPresent(found -> {
                Object client = adapt(found.getBeanInstance(), storeSpec, classLoader);
                beanFactory.registerDependentBean(found.getBeanName(), dependent);
                clients.put(store, client);
                log.debug("Store [{}] uses the bean [{}]", store, found.getBeanName());
            });
        });
        return clients;
    }

    /**
     * The stores the runtime opens, as {@code RuntimePlan} chooses them: those of the enabled segment and periodic
     * segment generators, and those of the leased machines the enabled time-based generators choose.
     *
     * @param spec the spec
     * @return the store names
     */
    static Set<String> storesInUse(RuntimeSpec spec) {
        Set<String> stores = new TreeSet<>();
        spec.generators().values().forEach(generator -> {
            switch (generator) {
                case GeneratorSpec.Segment segment -> stores.add(segment.store());
                case GeneratorSpec.PeriodicSegment periodic -> stores.add(periodic.store());
                case GeneratorSpec.Snowflake snowflake -> machineStore(spec, snowflake.machine()).ifPresent(stores::add);
                case GeneratorSpec.CosId80 cosId80 -> machineStore(spec, cosId80.machine()).ifPresent(stores::add);
                default -> {
                    // A disabled, custom, instance or uuidv7 generator uses no store.
                }
            }
        });
        return stores;
    }

    private static Optional<String> machineStore(RuntimeSpec spec, Optional<String> choice) {
        String name = choice.orElse(RuntimeSpec.DEFAULT_MACHINE);
        Optional<MachineSpec> machine = RuntimeSpec.DEFAULT_MACHINE.equals(name) ? spec.machine() : Optional.ofNullable(spec.machines().get(name));
        return machine.filter(MachineSpec.Leased.class::isInstance).map(leased -> ((MachineSpec.Leased) leased).store());
    }

    /**
     * The single, or primary, bean of the first client type of the store type that has one, as Spring resolves an
     * injection point: from this context, else from its ancestors (#1476), a bean of this context hiding theirs.
     */
    private static Optional<NamedBeanHolder<?>> find(String type, ConfigurableListableBeanFactory beanFactory, ClassLoader classLoader) {
        for (String className : CLIENT_TYPES.getOrDefault(type, List.of())) {
            if (!ClassUtils.isPresent(className, classLoader)) {
                continue;
            }
            Class<?> clientType = ClassUtils.resolveClassName(className, classLoader);
            try {
                return Optional.of(beanFactory.resolveNamedBean(clientType));
            } catch (NoUniqueBeanDefinitionException several) {
                log.info("Several {} beans for {} stores and none primary: set cosid.stores.<name>.client", className, type);
                return Optional.empty();
            } catch (NoSuchBeanDefinitionException none) {
                log.trace("No {} bean for {} stores", className, type);
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
