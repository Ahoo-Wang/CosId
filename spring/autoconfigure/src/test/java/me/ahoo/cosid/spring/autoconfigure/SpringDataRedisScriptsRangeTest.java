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

import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.store.redis.RedisStoreProvider;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.tck.RangeStoreContract;

import org.junit.jupiter.api.AfterAll;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * The range contract of the Redis store on {@link SpringDataRedisScripts}.
 */
class SpringDataRedisScriptsRangeTest extends RangeStoreContract {
    static final LettuceConnectionFactory FACTORY = TestRedis.lettuce();

    static me.ahoo.cosid.store.Store open() {
        return new RedisStoreProvider().open(new StoreConfig("spring", RedisStoreProvider.TYPE, Duration.ofSeconds(5), Map.of(),
            Optional.of(new SpringDataRedisScripts(FACTORY, Duration.ofSeconds(5), "auto"))));
    }

    @AfterAll
    static void destroy() {
        FACTORY.destroy();
    }

    @Override
    protected RangeStore newStore() {
        return open().ranges();
    }
}
