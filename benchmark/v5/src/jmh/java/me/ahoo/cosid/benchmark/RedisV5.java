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

package me.ahoo.cosid.benchmark;

import me.ahoo.cosid.runtime.StoreSpec;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.redis.RedisStoreProvider;

import java.time.Duration;

/**
 * The Redis of the P7 / P10 scenarios on 5.x: a store opened by the provider from {@code COSID_TEST_REDIS_URI}
 * (default {@code redis://localhost:6379}), {@code wait=auto}.
 */
final class RedisV5 {
    private RedisV5() {
    }

    static String uri() {
        String uri = System.getenv("COSID_TEST_REDIS_URI");
        return uri == null || uri.isBlank() ? "redis://localhost:6379" : uri;
    }

    static Store open() {
        return new RedisStoreProvider().open(StoreConfig.builder("bench", RedisStoreProvider.TYPE)
            .timeout(Duration.ofSeconds(10))
            .option("uri", uri())
            .build());
    }

    /** The store of P7: the same Redis, the store timeout within the reserve timeout of {@code BatchPolicy.DEFAULT}. */
    static StoreSpec chainStore() {
        return StoreSpec.builder(RedisStoreProvider.TYPE).timeout(Duration.ofSeconds(2)).option("uri", uri()).build();
    }
}
