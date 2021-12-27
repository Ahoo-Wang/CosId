/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.machine.*;
import me.ahoo.cosky.core.redis.RedisScripts;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer.getBackwardsTimeStamp;

/**
 * @author ahoo wang
 */
@Slf4j
public class RedisMachineIdDistributor extends AbstractMachineIdDistributor {
    public static final String MACHINE_ID_DISTRIBUTE = "machine_id_distribute.lua";
    public static final String MACHINE_ID_REVERT = "machine_id_revert.lua";
    public static final String MACHINE_ID_REVERT_STABLE = "machine_id_revert_stable.lua";
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
    protected MachineState distribute0(String namespace, int machineBit, InstanceId instanceId) {

        MachineState machineState = distributeAsync0(namespace, machineBit, instanceId).block(timeout);
        if (log.isInfoEnabled()) {
            log.info("distribute0 - [{}] - instanceId:[{}] - machineBit:[{}] @ namespace:[{}].", machineState, instanceId, machineBit, namespace);
        }
        return machineState;
    }

    protected Mono<MachineState> distributeAsync0(String namespace, int machineBit, InstanceId instanceId) {
        if (log.isInfoEnabled()) {
            log.info("distributeAsync0 - instanceId:[{}] - machineBit:[{}] @ namespace:[{}].", instanceId, machineBit, namespace);
        }
        return RedisScripts.doEnsureScript(MACHINE_ID_DISTRIBUTE, redisCommands,
                (scriptSha) -> {
                    String[] keys = {hashTag(namespace)};
                    String[] values = {instanceId.getInstanceId(), String.valueOf(maxMachineId(machineBit))};
                    return redisCommands.evalsha(scriptSha, ScriptOutputType.MULTI, keys, values).next();
                }
        ).map(distribution -> {
            @SuppressWarnings("unchecked")
            List<Long> state = (List<Long>) distribution;
            int realMachineId = state.get(0).intValue();

            if (realMachineId == -1) {
                throw new MachineIdOverflowException(totalMachineIds(machineBit), instanceId);
            }

            long lastStamp = NOT_FOUND_LAST_STAMP;
            if (state.size() == 2) {
                lastStamp = state.get(1);
            }

            return MachineState.of(realMachineId, lastStamp);
        });
    }


    @Override
    protected void revert0(String namespace, InstanceId instanceId, MachineState machineState) {
        revertAsync0(namespace, instanceId, machineState).block(timeout);
    }

    /**
     * when {@link InstanceId#isStable()} is true,do not revert machineId
     *
     * @param namespace
     * @param instanceId
     * @param machineState
     * @return
     */
    protected Mono<Void> revertAsync0(String namespace, InstanceId instanceId, MachineState machineState) {
        if (log.isInfoEnabled()) {
            log.info("revertAsync - [{}] instanceId:[{}] @ namespace:[{}].", machineState, instanceId, namespace);
        }
        if (instanceId.isStable()) {
            return revertScriptAsync(MACHINE_ID_REVERT_STABLE, namespace, instanceId, machineState);
        }

        return revertScriptAsync(MACHINE_ID_REVERT, namespace, instanceId, machineState);
    }

    private Mono<Void> revertScriptAsync(String scriptName, String namespace, InstanceId instanceId, MachineState machineState) {
        return RedisScripts.doEnsureScript(scriptName, redisCommands,
                (scriptSha) -> {
                    long lastStamp = machineState.getLastTimeStamp();
                    if (getBackwardsTimeStamp(lastStamp) < 0) {
                        lastStamp = System.currentTimeMillis();
                    }
                    String[] keys = {hashTag(namespace)};
                    String[] values = {instanceId.getInstanceId(), String.valueOf(lastStamp)};
                    return redisCommands.evalsha(scriptSha, ScriptOutputType.INTEGER, keys, values).then();
                }
        );
    }

    /**
     * redis hash tag for redis-cluster
     *
     * @param key
     * @return hash tag key
     */
    public static String hashTag(String key) {
        return "{" + key + "}";
    }

}
