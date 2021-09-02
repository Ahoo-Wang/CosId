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

package me.ahoo.cosid.spring.redis;

import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.machine.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.List;

import static me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer.getBackwardsTimeStamp;

/**
 * @author ahoo wang
 */
@Slf4j
public class SpringRedisMachineIdDistributor extends AbstractMachineIdDistributor {

    public static final String MACHINE_ID_DISTRIBUTE_SOURCE = Scripts.getScript("machine_id_distribute.lua");
    public static final RedisScript<List> MACHINE_ID_DISTRIBUTE = RedisScript.of(MACHINE_ID_DISTRIBUTE_SOURCE, List.class);

    public static final String MACHINE_ID_REVERT_SOURCE = Scripts.getScript("machine_id_revert.lua");
    public static final RedisScript<Long> MACHINE_ID_REVERT = RedisScript.of(MACHINE_ID_REVERT_SOURCE, Long.class);

    public static final String MACHINE_ID_REVERT_STABLE_SOURCE = Scripts.getScript("machine_id_revert_stable.lua");
    public static final RedisScript<Long> MACHINE_ID_REVERT_STABLE = RedisScript.of(MACHINE_ID_REVERT_STABLE_SOURCE, Long.class);

    private final StringRedisTemplate redisTemplate;

    public SpringRedisMachineIdDistributor(StringRedisTemplate redisTemplate,
                                           MachineStateStorage machineStateStorage,
                                           ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        super(machineStateStorage, clockBackwardsSynchronizer);

        this.redisTemplate = redisTemplate;
    }

    @Override
    protected MachineState distribute0(String namespace, int machineBit, InstanceId instanceId) {
        if (log.isInfoEnabled()) {
            log.info("distribute0 - instanceId:[{}] - machineBit:[{}] @ namespace:[{}].", instanceId, machineBit, namespace);
        }

        List<String> keys = Collections.singletonList(hashTag(namespace));
        Object[] values = {instanceId.getInstanceId(), String.valueOf(maxMachineId(machineBit))};
        @SuppressWarnings("unchecked")
        List<Long> state = (List<Long>) redisTemplate.execute(MACHINE_ID_DISTRIBUTE, keys, values);
        Preconditions.checkState(state != null, "state can not be null!");
        Preconditions.checkState(state.size() > 0, "state.size must be greater than 0!");
        int realMachineId = state.get(0).intValue();
        if (realMachineId == -1) {
            throw new MachineIdOverflowException(totalMachineIds(machineBit), instanceId);
        }
        long lastStamp = NOT_FOUND_LAST_STAMP;
        if (state.size() == 2) {
            lastStamp = state.get(1);
        }
        MachineState machineState = MachineState.of(realMachineId, lastStamp);
        if (log.isInfoEnabled()) {
            log.info("distribute0 - machineState:[{}] - instanceId:[{}] - machineBit:[{}] @ namespace:[{}].", machineState, instanceId, machineBit, namespace);
        }
        return machineState;
    }

    /**
     * when {@link InstanceId#isStable()} is true,do not revert machineId
     *
     * @param namespace
     * @param instanceId
     * @param machineState
     */
    @Override
    protected void revert0(String namespace, InstanceId instanceId, MachineState machineState) {
        if (log.isInfoEnabled()) {
            log.info("revert0 - instanceId:[{}] @ namespace:[{}].", instanceId, namespace);
        }
        RedisScript<Long> script = MACHINE_ID_REVERT;
        if (instanceId.isStable()) {
            script = MACHINE_ID_REVERT_STABLE;
        }
        long lastStamp = machineState.getLastTimeStamp();
        if (getBackwardsTimeStamp(lastStamp) < 0) {
            lastStamp = System.currentTimeMillis();
        }
        List<String> keys = Collections.singletonList(hashTag(namespace));
        Object[] values = {instanceId.getInstanceId(), String.valueOf(lastStamp)};

        redisTemplate.execute(script, keys, values);
    }

    /**
     * redis hash-tag for redis-cluster
     *
     * @param key
     * @return hash-tag key
     */
    public static String hashTag(String key) {
        return "{" + key + "}";
    }

}
