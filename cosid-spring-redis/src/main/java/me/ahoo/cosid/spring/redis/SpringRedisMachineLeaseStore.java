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

import static me.ahoo.cosid.spring.redis.RedisKeys.hashTag;

import me.ahoo.cosid.internal.Checks;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.lease.Lease;
import me.ahoo.cosid.machine.lease.MachineLeaseStore;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * {@link MachineLeaseStore} on Redis: one atomic Lua script per primitive ({@code machine_lease.lua}).
 *
 * <p>It uses the same keys as the 3.4 machine id distributor ({@code cosid:{namespace}:itc_idx}, {@code revert},
 * {@code adder}, {@code stable}), so instances running either one, and 3.4 instances, can share a namespace during an
 * upgrade. The watermark, renewal time and version of each machine id are kept in {@code cosid:{namespace}:lease}; the
 * 3.4 keys hold a timestamp that is at least all of them, which 3.4 reads as its last timestamp. A machine id last
 * written by a 3.4 instance is read with that timestamp as its watermark, renewal time and version. The server clock is
 * Redis' {@code TIME}. Every command is bounded by the {@link RedisCommandTimeout}.</p>
 *
 * @author ahoo wang
 * @since 3.4.0
 */
public class SpringRedisMachineLeaseStore implements MachineLeaseStore {
    @SuppressWarnings("rawtypes")
    public static final RedisScript<List> MACHINE_LEASE = RedisScript.of(new ClassPathResource("machine_lease.lua"), List.class);
    
    private final StringRedisTemplate redisTemplate;
    private final RedisCommandTimeout commandTimeout;
    
    /**
     * Create a Redis lease store.
     *
     * @param commandTimeout bounds every command, which may run under the lease distributor's lock: must be enabled
     */
    public SpringRedisMachineLeaseStore(StringRedisTemplate redisTemplate, RedisCommandTimeout commandTimeout) {
        this.redisTemplate = Checks.checkNotNull(redisTemplate, "redisTemplate can not be null!");
        this.commandTimeout = Checks.checkNotNull(commandTimeout, "commandTimeout can not be null!");
        Checks.checkArgument(commandTimeout.isEnabled(), "commandTimeout must be enabled: the lease adapter holds a lock during each call!");
    }
    
    /**
     * The same store with another command timeout, for example a longer one for the distribution at startup, where the
     * first command also pays for connecting.
     */
    public SpringRedisMachineLeaseStore withCommandTimeout(RedisCommandTimeout commandTimeout) {
        return new SpringRedisMachineLeaseStore(redisTemplate, commandTimeout);
    }
    
    public RedisCommandTimeout getCommandTimeout() {
        return commandTimeout;
    }
    
    /**
     * The keys of a namespace, as the script reads them: {@code cosid:{namespace}:itc_idx}, {@code revert},
     * {@code adder}, {@code stable}, {@code lease}. One hash tag, so one Redis Cluster slot.
     */
    static List<String> keys(String namespace) {
        String prefix = keyPrefix(namespace);
        return List.of(prefix + "itc_idx", prefix + "revert", prefix + "adder", prefix + "stable", prefix + "lease");
    }
    
    private static String keyPrefix(String namespace) {
        return "cosid:" + hashTag(namespace) + ":";
    }
    
    private static String stable(InstanceId instanceId) {
        return instanceId.isStable() ? "1" : "0";
    }
    
    private Optional<Lease> execute(String operation, String namespace, InstanceId instanceId, Object... arguments) {
        Object[] args = new Object[3 + arguments.length];
        args[0] = operation;
        args[1] = instanceId.getInstanceId();
        args[2] = stable(instanceId);
        for (int i = 0; i < arguments.length; i++) {
            args[3 + i] = String.valueOf(arguments[i]);
        }
        @SuppressWarnings("unchecked")
        List<Long> result = commandTimeout.call(operation,
            () -> (List<Long>) redisTemplate.execute(MACHINE_LEASE, keys(namespace), args));
        if (result == null || result.size() < 4) {
            return Optional.empty();
        }
        return Optional.of(new Lease(namespace, instanceId, result.get(0).intValue(), result.get(1), result.get(2), result.get(3)));
    }
    
    @Override
    public Optional<Lease> claimSelf(String namespace, InstanceId instanceId, long now) {
        return execute("claimSelf", namespace, instanceId, now);
    }
    
    @Override
    public Optional<Lease> claimReleased(String namespace, InstanceId instanceId, int maxMachineId, long now) {
        return execute("claimReleased", namespace, instanceId, now, maxMachineId);
    }
    
    @Override
    public Optional<Lease> claimNew(String namespace, InstanceId instanceId, int maxMachineId, long now) {
        return execute("claimNew", namespace, instanceId, now, maxMachineId);
    }
    
    @Override
    public Optional<Lease> claimExpired(String namespace, InstanceId instanceId, int maxMachineId, long expiredBefore, long now) {
        return execute("claimExpired", namespace, instanceId, now, maxMachineId, expiredBefore);
    }
    
    @Override
    public Optional<Lease> renew(Lease lease, long now) {
        return execute("renew", lease.getNamespace(), lease.getInstanceId(), now, lease.getMachineId(), lease.getVersion());
    }
    
    @Override
    public boolean release(Lease lease, long watermark) {
        return execute("release", lease.getNamespace(), lease.getInstanceId(), watermark, lease.getMachineId(), lease.getVersion()).isPresent();
    }
    
    /**
     * Redis' {@code TIME}, in milliseconds.
     */
    @Override
    public long serverTime() {
        Long time = commandTimeout.call("serverTime", () -> redisTemplate.execute((RedisCallback<Long>) connection -> connection.serverCommands().time()));
        return Checks.checkNotNull(time, "Redis TIME returned null!");
    }
    
    /**
     * Redis' {@code TIME}, in milliseconds, of the node that holds the namespace's keys: in Redis Cluster each node has its
     * own clock, and the leases of a namespace are judged by the clock of the node that runs their script.
     */
    @Override
    public long serverTime(String namespace) {
        byte[] slotKey = (keyPrefix(namespace) + "itc_idx").getBytes(StandardCharsets.UTF_8);
        Long time = commandTimeout.call("serverTime", () -> redisTemplate.execute((RedisCallback<Long>) connection -> {
            if (connection instanceof RedisClusterConnection clusterConnection) {
                RedisClusterNode node = clusterConnection.clusterGetNodeForKey(slotKey);
                return clusterConnection.serverCommands().time(node, TimeUnit.MILLISECONDS);
            }
            return connection.serverCommands().time();
        }));
        return Preconditions.checkNotNull(time, "Redis TIME returned null!");
    }
}
