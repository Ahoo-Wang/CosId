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

import me.ahoo.cosid.store.StoreProvider;

import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

import java.util.List;

/**
 * Native image hints (11 §5): the stores' {@link java.util.ServiceLoader} registrations and providers, the Redis Lua
 * scripts, the JDBC schemas and dialect registrations, the generator bean factory, and the endpoint's
 * {@link CosIdEndpoint.Generator} for JSON.
 */
public class CosIdRuntimeHints implements RuntimeHintsRegistrar {
    static final List<String> STORE_PROVIDERS = List.of(
        "me.ahoo.cosid.store.jdbc.JdbcStoreProvider",
        "me.ahoo.cosid.store.redis.RedisStoreProvider",
        "me.ahoo.cosid.store.mongo.MongoStoreProvider",
        "me.ahoo.cosid.store.zookeeper.ZookeeperStoreProvider",
        "me.ahoo.cosid.store.remote.RemoteStoreProvider");

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.resources()
            .registerPattern("META-INF/services/" + StoreProvider.class.getName())
            .registerPattern("META-INF/services/me.ahoo.cosid.store.jdbc.JdbcDialect")
            .registerPattern("me/ahoo/cosid/store/jdbc/schema-*.sql")
            .registerPattern("me/ahoo/cosid/store/redis/*.lua");
        for (String provider : STORE_PROVIDERS) {
            hints.reflection().registerTypeIfPresent(classLoader, provider, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
        }
        hints.reflection().registerType(TypeReference.of(CosIdGeneratorRegistrar.GeneratorBean.class), MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
        new BindingReflectionHintsRegistrar().registerReflectionHints(hints.reflection(), CosIdEndpoint.Generator.class);
    }
}
