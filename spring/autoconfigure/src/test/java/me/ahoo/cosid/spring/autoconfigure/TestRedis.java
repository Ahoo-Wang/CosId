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

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;

/**
 * The Redis of the tests, started once per test JVM: {@code COSID_TEST_REDIS_URI} when set (AGENTS.md), a
 * Testcontainers one otherwise.
 */
final class TestRedis {
    static final DockerImageName IMAGE = DockerImageName.parse(System.getProperty("cosid.test.redis.image", "redis:8.10-alpine"));
    private static String uri;

    private TestRedis() {
    }

    static synchronized String uri() {
        if (uri == null) {
            String given = System.getenv("COSID_TEST_REDIS_URI");
            if (given == null || given.isBlank()) {
                GenericContainer<?> redis = new GenericContainer<>(IMAGE).withExposedPorts(6379);
                redis.start();
                given = "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379);
            }
            uri = given;
        }
        return uri;
    }

    /** A started Lettuce connection factory on {@link #uri()}; the caller destroys it. */
    static LettuceConnectionFactory lettuce() {
        URI parsed = URI.create(uri());
        LettuceConnectionFactory factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(parsed.getHost(), parsed.getPort()));
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }
}
