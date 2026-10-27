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

import io.lettuce.core.RedisURI;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * The Redis of the P7 / P10 scenarios on 4.x: a {@code StringRedisTemplate} on Lettuce, as the 4.1.0 starter builds it,
 * at {@code COSID_TEST_REDIS_URI} (default {@code redis://localhost:6379}).
 */
final class Redis4x {
    private Redis4x() {
    }

    static String uri() {
        String uri = System.getenv("COSID_TEST_REDIS_URI");
        return uri == null || uri.isBlank() ? "redis://localhost:6379" : uri;
    }

    static LettuceConnectionFactory connectionFactory() {
        RedisURI uri = RedisURI.create(uri());
        LettuceConnectionFactory factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(uri.getHost(), uri.getPort()));
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }

    static StringRedisTemplate template(LettuceConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }
}
