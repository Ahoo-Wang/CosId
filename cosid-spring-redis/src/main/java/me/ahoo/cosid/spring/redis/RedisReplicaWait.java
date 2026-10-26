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

import me.ahoo.cosid.internal.Checks;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.ClassUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Function;

/**
 * Waits, after a write, until that many replicas acknowledged it ({@code WAIT numreplicas timeout}), on the
 * connection that wrote it: a failover inside the replication window would otherwise lose a machine id lease or a
 * segment increment that the caller already relies on. Too few replicas within the timeout is a failure
 * ({@link RedisReplicaWaitException}); the write itself may still be on the primary, which is safe for CosId (a lease
 * held until it expires, or a gap in the ids).
 *
 * <p>{@code WAIT} blocks its connection until it returns. With Lettuce's shared native connection (the default), the
 * other commands of the application queue behind it, up to the timeout: turn the sharing off
 * ({@code LettuceConnectionFactory#setShareNativeConnection(false)}, with a pool) to avoid that. On a Redis
 * Cluster it needs Lettuce, and runs on Lettuce's connection to the node of the write.</p>
 *
 * @author ahoo wang
 * @since 4.1.0
 */
public final class RedisReplicaWait {
    /**
     * No wait: the write returns as soon as the primary applied it, as before.
     */
    public static final RedisReplicaWait NONE = new RedisReplicaWait(0, Duration.ZERO);
    
    private static final boolean LETTUCE_PRESENT = ClassUtils.isPresent("io.lettuce.core.RedisClient", RedisReplicaWait.class.getClassLoader());
    
    private final int replicas;
    private final Duration timeout;
    
    private RedisReplicaWait(int replicas, Duration timeout) {
        this.replicas = replicas;
        this.timeout = timeout;
    }
    
    /**
     * Create a replica wait.
     *
     * @param replicas replicas that must acknowledge each write, {@code 0} for none
     * @param timeout  how long to wait for them, positive when {@code replicas} is: {@code WAIT} with 0 waits forever
     */
    public static RedisReplicaWait of(int replicas, @Nullable Duration timeout) {
        Checks.checkArgument(replicas >= 0, "replicas:[%s] can not be negative!", replicas);
        if (replicas == 0) {
            return NONE;
        }
        Checks.checkArgument(timeout != null && !timeout.isNegative() && timeout.toMillis() > 0,
            "timeout:[%s] must be at least 1ms when waiting for %s replicas!", timeout, replicas);
        return new RedisReplicaWait(replicas, timeout);
    }
    
    public int getReplicas() {
        return replicas;
    }
    
    public Duration getTimeout() {
        return timeout;
    }
    
    public boolean isEnabled() {
        return replicas > 0;
    }
    
    /**
     * Run a write, then wait for the replicas on the same connection.
     *
     * @param redisTemplate the template
     * @param slotKey       a key the write touches: in Redis Cluster the wait runs on the node that holds it
     * @param operation     name of the operation, for the failure message
     * @param write         the write, on the operations bound to one connection
     * @param <T>           result type
     * @return the result of the write
     * @throws RedisReplicaWaitException when fewer replicas acknowledged the write within the timeout
     */
    public <T> T execute(StringRedisTemplate redisTemplate, String slotKey, String operation, Function<RedisOperations<String, String>, T> write) {
        if (!isEnabled()) {
            return write.apply(redisTemplate);
        }
        return redisTemplate.execute(new SessionCallback<T>() {
            @SuppressWarnings("unchecked")
            @Override
            public <K, V> T execute(RedisOperations<K, V> operations) throws DataAccessException {
                RedisOperations<String, String> bound = (RedisOperations<String, String>) operations;
                T result = write.apply(bound);
                RedisCallback<Long> wait = connection -> waitForReplicas(connection, slotKey);
                // The connection itself, not a close-suppressing proxy: the Lettuce path needs its type.
                Long acknowledged = bound instanceof RedisTemplate<String, String> template ? template.execute(wait, true) : bound.execute(wait);
                if (acknowledged == null || acknowledged < replicas) {
                    throw new RedisReplicaWaitException(operation, replicas, acknowledged == null ? 0 : acknowledged, timeout);
                }
                return result;
            }
        });
    }
    
    private @Nullable Long waitForReplicas(RedisConnection connection, String slotKey) {
        if (LETTUCE_PRESENT && LettuceReplicaWait.isLettuce(connection)) {
            return LettuceReplicaWait.waitForReplicas(connection, slotKey, replicas, timeout.toMillis());
        }
        if (connection instanceof RedisClusterConnection) {
            // Each command may take another pooled connection to the node: WAIT would not see the write.
            throw new UnsupportedOperationException("The Redis replica wait on a Redis Cluster needs Lettuce.");
        }
        byte[] numReplicas = String.valueOf(replicas).getBytes(StandardCharsets.US_ASCII);
        byte[] timeoutMillis = String.valueOf(timeout.toMillis()).getBytes(StandardCharsets.US_ASCII);
        Object acknowledged = connection.execute("WAIT", numReplicas, timeoutMillis);
        return acknowledged instanceof Number number ? number.longValue() : null;
    }
    
    @Override
    public String toString() {
        return "RedisReplicaWait{replicas=" + replicas + ", timeout=" + timeout + '}';
    }
}
