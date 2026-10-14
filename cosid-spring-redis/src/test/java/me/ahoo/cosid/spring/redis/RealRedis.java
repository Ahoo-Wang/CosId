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

package me.ahoo.cosid.spring.redis;

import me.ahoo.cosid.test.container.RedisLauncher;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assumptions;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.URI;
import java.util.List;

/**
 * Builds a {@link StringRedisTemplate} connected to the real Redis provided by {@link RedisLauncher}.
 */
final class RealRedis {
    /**
     * {@code host:port,host:port,...} of a Redis Cluster: when set, every Redis test runs against the cluster.
     */
    static final String CLUSTER_NODES_ENV = "COSID_TEST_REDIS_CLUSTER_NODES";

    private RealRedis() {
    }

    /**
     * Skips the calling test class when neither {@code COSID_TEST_REDIS_URI} nor Docker is available.
     */
    static LettuceConnectionFactory createConnectionFactory() {
        String clusterNodes = clusterNodes();
        LettuceConnectionFactory connectionFactory;
        if (clusterNodes != null) {
            connectionFactory = new LettuceConnectionFactory(new RedisClusterConfiguration(List.of(clusterNodes.split(","))));
        } else {
            Assumptions.assumeTrue(RedisLauncher.isAvailable(), "Redis contract tests need Docker or " + RedisLauncher.URI_ENV);
            URI uri = URI.create(RedisLauncher.getUri());
            connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(uri.getHost(), uri.getPort()));
        }
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        return connectionFactory;
    }

    @Nullable
    static String clusterNodes() {
        String nodes = System.getenv(CLUSTER_NODES_ENV);
        return nodes == null || nodes.isBlank() ? null : nodes.trim();
    }
    
    static StringRedisTemplate createRedisTemplate(LettuceConnectionFactory connectionFactory) {
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
}
