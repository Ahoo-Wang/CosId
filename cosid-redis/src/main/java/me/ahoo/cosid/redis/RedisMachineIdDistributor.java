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

package me.ahoo.cosid.redis;

import static me.ahoo.cosid.machine.ClockBackwardsSynchronizer.getBackwardsTimeStamp;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.AbstractMachineIdDistributor;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineIdOverflowException;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosky.core.redis.RedisScripts;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Redis MachineIdDistributor.
 *
 * @author ahoo wang
 */
@Slf4j
public class RedisMachineIdDistributor extends AbstractMachineIdDistributor {
    public static final String MACHINE_ID_DISTRIBUTE = "machine_id_distribute.lua";
    public static final String MACHINE_ID_REVERT = "machine_id_revert.lua";
    public static final String MACHINE_ID_REVERT_STABLE = "machine_id_revert_stable.lua";
    public static final String MACHINE_ID_GUARD = "machine_id_guard.lua";
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(1);
    public final Duration timeout;
    private final RedisClusterReactiveCommands<String, String> redisCommands;
    
    public RedisMachineIdDistributor(RedisClusterReactiveCommands<String, String> redisCommands,
                                     MachineStateStorage machineStateStorage,
                                     ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        this(DEFAULT_TIMEOUT, redisCommands, machineStateStorage, clockBackwardsSynchronizer);
    }
    
    public RedisMachineIdDistributor(Duration timeout, RedisClusterReactiveCommands<String, String> redisCommands,
                                     MachineStateStorage machineStateStorage,
                                     ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        super(machineStateStorage, clockBackwardsSynchronizer);
        this.timeout = timeout;
        this.redisCommands = redisCommands;
    }
    
    
    @Override
    protected MachineState distributeRemote(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
        
        MachineState machineState = distributeAsync(namespace, machineBit, instanceId, safeGuardDuration).block(timeout);
        if (log.isInfoEnabled()) {
            log.info("Distribute Remote [{}] - instanceId:[{}] - machineBit:[{}] @ namespace:[{}].", machineState, instanceId, machineBit, namespace);
        }
        return machineState;
    }
    
    protected Mono<MachineState> distributeAsync(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
        if (log.isInfoEnabled()) {
            log.info("Distribute Async instanceId:[{}] - machineBit:[{}] @ namespace:[{}].", instanceId, machineBit, namespace);
        }
        return RedisScripts.doEnsureScript(MACHINE_ID_DISTRIBUTE, redisCommands,
            (scriptSha) -> {
                String[] keys = {hashTag(namespace)};
                String[] values = {
                    instanceId.getInstanceId(),
                    String.valueOf(MachineIdDistributor.maxMachineId(machineBit)),
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(MachineIdDistributor.getSafeGuardAt(safeGuardDuration, instanceId.isStable()))
                };
                return redisCommands.evalsha(scriptSha, ScriptOutputType.MULTI, keys, values).next();
            }
        ).map(distribution -> {
            @SuppressWarnings("unchecked")
            List<Long> state = (List<Long>) distribution;
            int realMachineId = state.get(0).intValue();
            
            if (realMachineId == -1) {
                throw new MachineIdOverflowException(MachineIdDistributor.totalMachineIds(machineBit), instanceId);
            }
            
            long lastStamp = NOT_FOUND_LAST_STAMP;
            if (state.size() == 2) {
                lastStamp = state.get(1);
            }
            
            return MachineState.of(realMachineId, lastStamp);
        });
    }
    
    
    @Override
    protected void revertRemote(String namespace, InstanceId instanceId, MachineState machineState) {
        revertAsync(namespace, instanceId, machineState).block(timeout);
    }
    
    @Override
    protected void guardRemote(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) {
        if (log.isInfoEnabled()) {
            log.info("Guard Remote instanceId:[{}]@[{}] - machineState:[{}].", instanceId, namespace, machineState);
        }
        Long affected = RedisScripts.doEnsureScript(MACHINE_ID_GUARD, redisCommands,
                (scriptSha) -> {
                    String[] keys = {hashTag(namespace)};
                    String[] values = {instanceId.getInstanceId(), String.valueOf(machineState.getLastTimeStamp())};
                    return redisCommands.evalsha(scriptSha, ScriptOutputType.MULTI, keys, values).next();
                }
            ).map(result -> {
                @SuppressWarnings("unchecked")
                List<Long> state = (List<Long>) result;
                return state.get(0);
            })
            .cast(Long.class)
            .block(timeout);
        if (null != affected && 0 == affected) {
            throw new MachineIdLostException(namespace, instanceId, machineState);
        }
    }
    
    /**
     * when {@link InstanceId#isStable()} is true,do not revert machineId.
     *
     * @param namespace namespace
     * @param instanceId instanceId
     * @param machineState machineState
     * @return Void of Mono
     */
    protected Mono<Void> revertAsync(String namespace, InstanceId instanceId, MachineState machineState) {
        if (log.isInfoEnabled()) {
            log.info("Revert Async [{}] instanceId:[{}] @ namespace:[{}].", machineState, instanceId, namespace);
        }
        if (instanceId.isStable()) {
            return revertScriptAsync(MACHINE_ID_REVERT_STABLE, namespace, instanceId, machineState);
        }
        
        return revertScriptAsync(MACHINE_ID_REVERT, namespace, instanceId, machineState);
    }
    
    private Mono<Void> revertScriptAsync(String scriptName, String namespace, InstanceId instanceId, MachineState machineState) {
        return RedisScripts.doEnsureScript(scriptName, redisCommands,
            (scriptSha) -> {
                long lastStamp = getLastStamp(machineState);
                String[] keys = {hashTag(namespace)};
                String[] values = {instanceId.getInstanceId(), String.valueOf(lastStamp)};
                return redisCommands.evalsha(scriptSha, ScriptOutputType.INTEGER, keys, values).then();
            }
        );
    }
    
    private long getLastStamp(MachineState machineState) {
        long lastStamp = machineState.getLastTimeStamp();
        if (getBackwardsTimeStamp(lastStamp) < 0) {
            lastStamp = System.currentTimeMillis();
        }
        return lastStamp;
    }
    
    /**
     * redis hash tag for redis-cluster.
     *
     * @param key key
     * @return hash tag key
     */
    public static String hashTag(String key) {
        return "{" + key + "}";
    }
    
}
