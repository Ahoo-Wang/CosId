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

import io.lettuce.core.RedisFuture;
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.output.IntegerOutput;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnection;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

/**
 * {@code WAIT} on the Lettuce connection that ran the write. Loaded only when Lettuce is on the classpath.
 */
final class LettuceReplicaWait {
    private LettuceReplicaWait() {
    }
    
    static boolean isLettuce(RedisConnection connection) {
        return connection instanceof LettuceConnection;
    }
    
    @SuppressWarnings("unchecked")
    static @Nullable Long waitForReplicas(RedisConnection connection, String slotKey, int replicas, long timeoutMillis) {
        if (connection instanceof RedisClusterConnection clusterConnection
            && connection.getNativeConnection() instanceof RedisAdvancedClusterAsyncCommands<?, ?> cluster) {
            // Lettuce keeps one write connection per node, shared by the commands routed to its slots: the script's.
            RedisClusterNode node = Checks.checkNotNull(clusterConnection.clusterGetNodeForKey(slotKey.getBytes(StandardCharsets.UTF_8)),
                "No Redis Cluster node serves the key!");
            String host = Checks.checkNotNull(node.getHost(), "The Redis Cluster node has no host!");
            Integer port = Checks.checkNotNull(node.getPort(), "The Redis Cluster node has no port!");
            RedisFuture<Long> acknowledged = ((RedisAdvancedClusterAsyncCommands<byte[], byte[]>) cluster)
                .getConnection(host, port).waitForReplication(replicas, timeoutMillis);
            try {
                return acknowledged.get();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new RedisSystemException("Interrupted waiting for the replicas.", interrupted);
            } catch (ExecutionException failure) {
                throw new RedisSystemException("WAIT failed.", failure.getCause());
            }
        }
        byte[] numReplicas = String.valueOf(replicas).getBytes(StandardCharsets.US_ASCII);
        byte[] timeout = String.valueOf(timeoutMillis).getBytes(StandardCharsets.US_ASCII);
        return (Long) ((LettuceConnection) connection).execute("WAIT", new IntegerOutput<>(ByteArrayCodec.INSTANCE), numReplicas, timeout);
    }
}
