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

package me.ahoo.cosid.machine;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * Manual machine ID distributor.
 *
 * <p>Uses a manually configured machine ID instead of dynamically
 * distributing from a centralized store.
 *
 * @author ahoo wang
 */
@Slf4j
public class ManualMachineIdDistributor extends AbstractMachineIdDistributor {

    private final int machineId;
    private final MachineState machineState;

    /**
     * Creates a manual distributor.
     *
     * @param machineId                the fixed machine ID to use
     * @param machineStateStorage    the state storage
     * @param clockBackwardsSynchronizer the clock synchronizer
     */
    public ManualMachineIdDistributor(int machineId, MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        super(machineStateStorage, clockBackwardsSynchronizer);
        this.machineId = machineId;
        this.machineState = MachineState.of(machineId, NOT_FOUND_LAST_STAMP);
    }

    /**
     * Gets the machine ID.
     *
     * @return the machine ID
     */
    public int getMachineId() {
        return machineId;
    }

    @Override
    protected MachineState distributeRemote(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
        if (log.isInfoEnabled()) {
            log.info("Distribute Remote machineState:[{}] - instanceId:[{}] - machineBit:[{}] @ namespace:[{}].", machineState, instanceId, machineBit, namespace);
        }
        return machineState;
    }

    @Override
    protected void revertRemote(String namespace, InstanceId instanceId, MachineState machineState) {

    }

    @Override
    protected void guardRemote(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) {

    }


}
