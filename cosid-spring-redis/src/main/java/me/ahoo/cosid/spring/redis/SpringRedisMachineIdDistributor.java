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

import static me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer.getBackwardsTimeStamp;

import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.machine.AbstractMachineIdDistributor;
import me.ahoo.cosid.snowflake.machine.InstanceId;
import me.ahoo.cosid.snowflake.machine.MachineIdDistributor;
import me.ahoo.cosid.snowflake.machine.MachineIdLostException;
import me.ahoo.cosid.snowflake.machine.MachineIdOverflowException;
import me.ahoo.cosid.snowflake.machine.MachineState;
import me.ahoo.cosid.snowflake.machine.MachineStateStorage;

import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Spring Redis MachineIdDistributor.
 *
 * <p><img src="doc-files/RedisMachineIdDistributor.png" alt="RedisMachineIdDistributor"></p>
 *
 * @author ahoo wang
 */
@Slf4j
public class SpringRedisMachineIdDistributor extends AbstractMachineIdDistributor {
    
    public static final Resource MACHINE_ID_DISTRIBUTE_SOURCE = new ClassPathResource("machine_id_distribute.lua");
    @SuppressWarnings("rawtypes")
    public static final RedisScript<List> MACHINE_ID_DISTRIBUTE = RedisScript.of(MACHINE_ID_DISTRIBUTE_SOURCE, List.class);
    
    public static final Resource MACHINE_ID_REVERT_SOURCE = new ClassPathResource("machine_id_revert.lua");
    public static final RedisScript<Long> MACHINE_ID_REVERT = RedisScript.of(MACHINE_ID_REVERT_SOURCE, Long.class);
    
    public static final Resource MACHINE_ID_REVERT_STABLE_SOURCE = new ClassPathResource("machine_id_revert_stable.lua");
    public static final RedisScript<Long> MACHINE_ID_REVERT_STABLE = RedisScript.of(MACHINE_ID_REVERT_STABLE_SOURCE, Long.class);
    
    public static final Resource MACHINE_ID_GUARD_SOURCE = new ClassPathResource("machine_id_guard.lua");
    public static final RedisScript<Long> MACHINE_ID_GUARD = RedisScript.of(MACHINE_ID_GUARD_SOURCE, Long.class);
    
    private final StringRedisTemplate redisTemplate;
    
    public SpringRedisMachineIdDistributor(StringRedisTemplate redisTemplate,
                                           MachineStateStorage machineStateStorage,
                                           ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        this(redisTemplate, machineStateStorage, clockBackwardsSynchronizer, FOREVER_SAFE_GUARD_DURATION);
    }
    
    public SpringRedisMachineIdDistributor(StringRedisTemplate redisTemplate,
                                           MachineStateStorage machineStateStorage,
                                           ClockBackwardsSynchronizer clockBackwardsSynchronizer,
                                           Duration safeGuardDuration) {
        super(machineStateStorage, clockBackwardsSynchronizer, safeGuardDuration);
        
        this.redisTemplate = redisTemplate;
    }
    
    @Override
    protected MachineState distributeRemote(String namespace, int machineBit, InstanceId instanceId) {
        if (log.isInfoEnabled()) {
            log.info("distributeRemote - instanceId:[{}] - machineBit:[{}] @ namespace:[{}].", instanceId, machineBit, namespace);
        }
        
        List<String> keys = Collections.singletonList(hashTag(namespace));
        Object[] values = {instanceId.getInstanceId(), String.valueOf(MachineIdDistributor.maxMachineId(machineBit)), String.valueOf(System.currentTimeMillis()),
            String.valueOf(getSafeGuardAt(instanceId.isStable()))};
        @SuppressWarnings("unchecked")
        List<Long> state = (List<Long>) redisTemplate.execute(MACHINE_ID_DISTRIBUTE, keys, values);
        assert state != null;
        Preconditions.checkNotNull(state, "state can not be null!");
        Preconditions.checkState(!state.isEmpty(), "state can not be empty!");
        Long machineId = state.get(0);
        assert null != machineId;
        int realMachineId = machineId.intValue();
        if (realMachineId == -1) {
            throw new MachineIdOverflowException(MachineIdDistributor.totalMachineIds(machineBit), instanceId);
        }
        long lastStamp = NOT_FOUND_LAST_STAMP;
        if (state.size() == 2) {
            lastStamp = state.get(1);
        }
        MachineState machineState = MachineState.of(realMachineId, lastStamp);
        if (log.isInfoEnabled()) {
            log.info("distributeRemote - machineState:[{}] - instanceId:[{}] - machineBit:[{}] @ namespace:[{}].", machineState, instanceId, machineBit, namespace);
        }
        return machineState;
    }
    
    /**
     * when {@link InstanceId#isStable()} is true,do not revert machineId.
     */
    @Override
    protected void revertRemote(String namespace, InstanceId instanceId, MachineState machineState) {
        if (log.isInfoEnabled()) {
            log.info("revertRemote - [{}] instanceId:[{}] @ namespace:[{}].", machineState, instanceId, namespace);
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
    
    @Override
    protected void guardRemote(String namespace, InstanceId instanceId, MachineState machineState) {
        if (log.isInfoEnabled()) {
            log.info("guardRemote - [{}] instanceId:[{}] @ namespace:[{}].", machineState, instanceId, namespace);
        }
        
        List<String> keys = Collections.singletonList(hashTag(namespace));
        Object[] values = {instanceId.getInstanceId(), String.valueOf(machineState.getLastTimeStamp())};
        
        Long affected = redisTemplate.<Long>execute(MACHINE_ID_GUARD, keys, values);
        
        if (null != affected && 0 == affected) {
            throw new MachineIdLostException(namespace, instanceId, machineState);
        }
    }
    
    /**
     * redis hash-tag for redis-cluster.
     *
     * @param key key
     * @return hash-tag key
     */
    public static String hashTag(String key) {
        return "{" + key + "}";
    }
    
}
