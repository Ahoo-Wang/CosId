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

package me.ahoo.cosid.spring.boot.starter;

import me.ahoo.cosid.spring.redis.RedisCommandTimeout;
import me.ahoo.cosid.spring.redis.RedisReplicaWait;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.util.ClassUtils;

import java.time.Duration;
import java.util.Map;

/**
 * The {@code cosid.*.redis.wait-replicas} and {@code wait-timeout} of CosId's Redis writes, checked at startup against
 * the timeouts that bound them and the connection factory that runs them.
 *
 * <ul>
 *     <li>{@code wait-timeout} must be shorter than each CosId command timeout that bounds the write and its wait, and
 *     than Lettuce's command timeout ({@code spring.data.redis.timeout}): otherwise a slow replica fails the call as a
 *     timeout, not as too few replicas, and the application's commands queued behind it time out as well.</li>
 *     <li>Jedis can not wait on a Redis Cluster: each command may take another pooled connection to the node.</li>
 *     <li>With Lettuce's shared native connection (Spring Boot's default), {@code WAIT} blocks every Redis command of
 *     the application for up to {@code wait-timeout}: logged at WARN.</li>
 * </ul>
 *
 * @author ahoo wang
 * @since 4.1.0
 */
@Slf4j
public final class RedisReplicaWaits {
    private static final ClassLoader CLASS_LOADER = RedisReplicaWaits.class.getClassLoader();
    private static final boolean LETTUCE_PRESENT = ClassUtils.isPresent("io.lettuce.core.RedisClient", CLASS_LOADER);
    private static final boolean JEDIS_PRESENT = ClassUtils.isPresent("redis.clients.jedis.Jedis", CLASS_LOADER);

    private RedisReplicaWaits() {
    }

    /**
     * The replica wait of {@code prefix.wait-replicas} and {@code prefix.wait-timeout}.
     *
     * @param prefix            the property prefix, such as {@code cosid.machine.distributor.redis}
     * @param connectionFactory the factory of the connections the writes run on, if known
     * @param boundingTimeouts  the CosId command timeouts that bound the write and its wait, by property name
     * @throws IllegalStateException when {@code wait-timeout} is not shorter than a timeout that bounds it, or the
     *                               client can not wait on this Redis
     */
    public static RedisReplicaWait of(String prefix, int replicas, @Nullable Duration waitTimeout, @Nullable RedisConnectionFactory connectionFactory,
                                      Map<String, RedisCommandTimeout> boundingTimeouts) {
        RedisReplicaWait replicaWait = RedisReplicaWait.of(replicas, waitTimeout);
        if (!replicaWait.isEnabled()) {
            return replicaWait;
        }
        String waitTimeoutProperty = prefix + ".wait-timeout";
        boundingTimeouts.forEach((property, timeout) -> {
            if (timeout.isEnabled()) {
                checkShorter(replicaWait, waitTimeoutProperty, property, timeout.getTimeout());
            }
        });
        if (connectionFactory == null) {
            return replicaWait;
        }
        if (LETTUCE_PRESENT) {
            Lettuce.check(replicaWait, prefix, connectionFactory);
        }
        if (JEDIS_PRESENT) {
            Jedis.check(prefix, connectionFactory);
        }
        return replicaWait;
    }

    private static void checkShorter(RedisReplicaWait replicaWait, String waitTimeoutProperty, String boundProperty, Duration bound) {
        if (replicaWait.getTimeout().compareTo(bound) >= 0) {
            throw new IllegalStateException(waitTimeoutProperty + " [" + replicaWait.getTimeout() + "] must be shorter than " + boundProperty
                + " [" + bound + "], which bounds the write and its wait.");
        }
    }

    /**
     * Loaded only when Lettuce is on the classpath.
     */
    private static final class Lettuce {
        private Lettuce() {
        }

        static void check(RedisReplicaWait replicaWait, String prefix, RedisConnectionFactory connectionFactory) {
            if (!(connectionFactory instanceof LettuceConnectionFactory lettuce)) {
                return;
            }
            checkShorter(replicaWait, prefix + ".wait-timeout", "spring.data.redis.timeout (the Lettuce command timeout)",
                lettuce.getClientConfiguration().getCommandTimeout());
            if (lettuce.getShareNativeConnection() && log.isWarnEnabled()) {
                log.warn("{}.wait-replicas={}: Lettuce shares one native connection (Spring Boot's default), so each WAIT after a CosId write holds "
                        + "every Redis command of the application for up to {}.wait-timeout [{}]. To avoid it, call setShareNativeConnection(false) "
                        + "on the LettuceConnectionFactory (a BeanPostProcessor, or your own factory bean), with "
                        + "spring.data.redis.lettuce.pool.enabled=true and commons-pool2.",
                    prefix, replicaWait.getReplicas(), prefix, replicaWait.getTimeout());
            }
        }
    }

    /**
     * Loaded only when Jedis is on the classpath.
     */
    private static final class Jedis {
        private Jedis() {
        }

        static void check(String prefix, RedisConnectionFactory connectionFactory) {
            if (connectionFactory instanceof JedisConnectionFactory jedis && jedis.isRedisClusterAware()) {
                throw new IllegalStateException(prefix + ".wait-replicas needs Lettuce on a Redis Cluster: with Jedis, WAIT may run on another "
                    + "connection than the write. Use Lettuce, or set " + prefix + ".wait-replicas=0.");
            }
        }
    }
}
