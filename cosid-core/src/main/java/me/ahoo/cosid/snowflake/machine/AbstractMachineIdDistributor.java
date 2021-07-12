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

package me.ahoo.cosid.snowflake.machine;

import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;

/**
 * @author ahoo wang
 */
@Slf4j
public abstract class AbstractMachineIdDistributor implements MachineIdDistributor {
    public static final int NOT_FOUND_LAST_STAMP = -1;
    private final MachineStateStorage machineStateStorage;
    private final ClockBackwardsSynchronizer clockBackwardsSynchronizer;

    public AbstractMachineIdDistributor(MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        this.machineStateStorage = machineStateStorage;
        this.clockBackwardsSynchronizer = clockBackwardsSynchronizer;
    }

    /**
     * 1. get from {@link MachineStateStorage}
     * 2. when not found: {@link #distribute0}
     * 3. set {@link me.ahoo.cosid.snowflake.machine.MachineState} to {@link MachineStateStorage}
     *
     * @param namespace
     * @param machineBit
     * @param instanceId
     * @return
     * @throws MachineIdOverflowException
     */
    @Override
    public int distribute(String namespace, int machineBit, InstanceId instanceId) throws MachineIdOverflowException {
        MachineState localState = machineStateStorage.get(namespace, instanceId);
        if (!MachineState.NOT_FOUND.equals(localState)) {
            clockBackwardsSynchronizer.syncUninterruptibly(localState.getLastTimeStamp());
            return localState.getMachineId();
        }

        localState = distribute0(namespace, machineBit, instanceId);
        if (ClockBackwardsSynchronizer.getBackwardsTimeStamp(localState.getLastTimeStamp()) > 0) {
            clockBackwardsSynchronizer.syncUninterruptibly(localState.getLastTimeStamp());
            localState = MachineState.of(localState.getMachineId(), System.currentTimeMillis());
        }

        machineStateStorage.set(namespace, localState.getMachineId(), instanceId);
        return localState.getMachineId();
    }

    protected abstract MachineState distribute0(String namespace, int machineBit, InstanceId instanceId);


    /**
     * 1. get from {@link MachineStateStorage}
     * 2. when not found: {@link #distribute0} , no need to revert
     * 3. revert
     *
     * @param namespace
     * @param instanceId
     * @throws MachineIdOverflowException
     */
    @Override
    public void revert(String namespace, InstanceId instanceId) throws MachineIdOverflowException {
        MachineState lastLocalState = machineStateStorage.get(namespace, instanceId);
        if (MachineState.NOT_FOUND.equals(lastLocalState)) {
            revert0(namespace, instanceId, lastLocalState);
            return;
        }
        if (ClockBackwardsSynchronizer.getBackwardsTimeStamp(lastLocalState.getLastTimeStamp()) < 0) {
            machineStateStorage.set(namespace, lastLocalState.getMachineId(), instanceId);
        }
        revert0(namespace, instanceId, lastLocalState);
    }


    protected abstract void revert0(String namespace, InstanceId instanceId, MachineState machineState);

}
