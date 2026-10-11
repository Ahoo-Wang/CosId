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

package me.ahoo.cosid.test.container;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Provides a real Redis for backend contract tests.
 *
 * <p>Uses {@code cosid.test.redis.uri} / {@code COSID_TEST_REDIS_URI} (for example {@code redis://localhost:6379})
 * when set, otherwise starts a shared Redis container.
 */
public final class RedisLauncher {
    public static final String URI_PROPERTY = "cosid.test.redis.uri";
    public static final String URI_ENV = "COSID_TEST_REDIS_URI";
    private static final int REDIS_PORT = 6379;
    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
        .withExposedPorts(REDIS_PORT);

    private RedisLauncher() {
    }

    /**
     * Redis URI in the form {@code redis://host:port}.
     *
     * @return redis uri
     */
    public static String getUri() {
        return ExplicitSetting.resolve(URI_PROPERTY, URI_ENV).orElseGet(() -> {
            REDIS_CONTAINER.start();
            return "redis://" + REDIS_CONTAINER.getHost() + ":" + REDIS_CONTAINER.getMappedPort(REDIS_PORT);
        });
    }
}
