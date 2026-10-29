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

import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.store.redis.RedisStoreProvider;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.tck.SlotLeaseStoreContract;

import org.junit.jupiter.api.AfterAll;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

/**
 * The slot lease contract of the Redis store on {@link SpringDataRedisScripts}.
 */
class SpringDataRedisScriptsSlotTest extends SlotLeaseStoreContract {
    static final LettuceConnectionFactory FACTORY = TestRedis.lettuce();

    @AfterAll
    static void destroy() {
        FACTORY.destroy();
    }

    @Override
    protected SlotLeaseStore newStore() {
        return new RedisStoreProvider().open(StoreConfig.builder("spring", RedisStoreProvider.TYPE)
            .timeout(Duration.ofSeconds(5))
            .client(new SpringDataRedisScripts(FACTORY, Duration.ofSeconds(5), "off"))
            .build()).slots();
    }
}
