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

package me.ahoo.cosid.snowflake.machine;

import static me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer.getBackwardsTimeStamp;

import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * Abstract MachineIdDistributor.
 *
 * @author ahoo wang
 */
@Slf4j
public abstract class AbstractMachineIdDistributor implements MachineIdDistributor {
    public static final int NOT_FOUND_LAST_STAMP = -1;
    public static final Duration FOREVER_SAFE_GUARD_DURATION = Duration.ofMillis(Long.MAX_VALUE);
    private final MachineStateStorage machineStateStorage;
    private final ClockBackwardsSynchronizer clockBackwardsSynchronizer;
    private final Duration safeGuardDuration;
    
    public AbstractMachineIdDistributor(MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        this(machineStateStorage, clockBackwardsSynchronizer, FOREVER_SAFE_GUARD_DURATION);
    }
    
    public AbstractMachineIdDistributor(MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer, Duration safeGuardDuration) {
        this.machineStateStorage = machineStateStorage;
        this.clockBackwardsSynchronizer = clockBackwardsSynchronizer;
        this.safeGuardDuration = safeGuardDuration;
    }
    
    public Duration getSafeGuardDuration() {
        return safeGuardDuration;
    }
    
    public long getSafeGuardAt(boolean stable) {
        if (stable) {
            return 0L;
        }
        
        if (FOREVER_SAFE_GUARD_DURATION.equals(safeGuardDuration)) {
            return 0L;
        }
        
        long safeGuardAt = System.currentTimeMillis() - safeGuardDuration.toMillis();
        if (safeGuardAt < 0) {
            return 0L;
        }
        return safeGuardAt;
    }
    
    /**
     * 1. get from {@link MachineStateStorage}
     * 2. when not found: {@link #distributeRemote}
     * 3. set {@link me.ahoo.cosid.snowflake.machine.MachineState} to {@link MachineStateStorage}
     *
     * @param namespace namespace
     * @param machineBit machineBit
     * @param instanceId instanceId
     * @return Machine Id
     * @throws MachineIdOverflowException This exception is thrown when the machine number allocation exceeds the threshold
     */
    @Override
    public int distribute(String namespace, int machineBit, InstanceId instanceId) throws MachineIdOverflowException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(machineBit > 0, "machineBit:[%s] must be greater than 0!", machineBit);
        Preconditions.checkNotNull(instanceId, "instanceId can not be null!");
        
        MachineState localState = machineStateStorage.get(namespace, instanceId);
        if (!MachineState.NOT_FOUND.equals(localState)) {
            clockBackwardsSynchronizer.syncUninterruptibly(localState.getLastTimeStamp());
            return localState.getMachineId();
        }
        
        localState = distributeRemote(namespace, machineBit, instanceId);
        if (ClockBackwardsSynchronizer.getBackwardsTimeStamp(localState.getLastTimeStamp()) > 0) {
            clockBackwardsSynchronizer.syncUninterruptibly(localState.getLastTimeStamp());
            localState = MachineState.of(localState.getMachineId(), System.currentTimeMillis());
        }
        
        machineStateStorage.set(namespace, localState.getMachineId(), instanceId);
        return localState.getMachineId();
    }
    
    protected abstract MachineState distributeRemote(String namespace, int machineBit, InstanceId instanceId);
    
    
    /**
     * 1. get from {@link MachineStateStorage}
     * 2. when not found: {@link #distributeRemote} , no need to revert
     * 3. revert
     *
     * @param namespace namespace
     * @param instanceId instanceId
     * @throws MachineIdOverflowException This exception is thrown when the machine number allocation exceeds the threshold
     */
    @Override
    public void revert(String namespace, InstanceId instanceId) throws MachineIdOverflowException {
        MachineState lastLocalState = resetStorage(namespace, instanceId);
        
        revertRemote(namespace, instanceId, lastLocalState);
    }
    
    protected abstract void revertRemote(String namespace, InstanceId instanceId, MachineState machineState);
    
    @Override
    public void guard(String namespace, InstanceId instanceId) {
        MachineState lastLocalState = resetStorage(namespace, instanceId);
        guardRemote(namespace, instanceId, lastLocalState);
    }
    
    private MachineState resetStorage(String namespace, InstanceId instanceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkNotNull(instanceId, "instanceId can not be null!");
        
        MachineState lastLocalState = machineStateStorage.get(namespace, instanceId);
        Preconditions.checkState(!MachineState.NOT_FOUND.equals(lastLocalState), "lastLocalState can not found!");
        if (getBackwardsTimeStamp(lastLocalState.getLastTimeStamp()) < 0) {
            lastLocalState = MachineState.of(lastLocalState.getMachineId(), System.currentTimeMillis());
            machineStateStorage.set(namespace, lastLocalState.getMachineId(), instanceId);
        }
        return lastLocalState;
    }
    
    protected abstract void guardRemote(String namespace, InstanceId instanceId, MachineState machineState);
    
}
