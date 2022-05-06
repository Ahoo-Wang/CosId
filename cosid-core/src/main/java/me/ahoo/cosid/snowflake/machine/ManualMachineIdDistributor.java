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

import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;

import lombok.extern.slf4j.Slf4j;

/**
 * Manual Machine Id Distributor.
 *
 * @author ahoo wang
 */
@Slf4j
public class ManualMachineIdDistributor extends AbstractMachineIdDistributor {

    private final int machineId;
    private final MachineState machineState;

    public ManualMachineIdDistributor(int machineId, MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        super(machineStateStorage, clockBackwardsSynchronizer);
        this.machineId = machineId;
        this.machineState = MachineState.of(machineId, NOT_FOUND_LAST_STAMP);
    }

    public int getMachineId() {
        return machineId;
    }

    @Override
    protected MachineState distributeRemote(String namespace, int machineBit, InstanceId instanceId) {
        if (log.isInfoEnabled()) {
            log.info("distribute0 - machineState:[{}] - instanceId:[{}] - machineBit:[{}] @ namespace:[{}].", machineState, instanceId, machineBit, namespace);
        }
        return machineState;
    }

    @Override
    protected void revertRemote(String namespace, InstanceId instanceId, MachineState machineState) {

    }
    
    @Override
    protected void guardRemote(String namespace, InstanceId instanceId, MachineState machineState) {
    
    }
    
    
}
