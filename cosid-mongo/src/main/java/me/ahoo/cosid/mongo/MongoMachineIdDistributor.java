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

package me.ahoo.cosid.mongo;

import me.ahoo.cosid.machine.AbstractMachineIdDistributor;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.MachineStateStorage;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

@Slf4j
public class MongoMachineIdDistributor extends AbstractMachineIdDistributor {
    private final MachineCollection machineCollection;
    
    public MongoMachineIdDistributor(MachineCollection machineCollection,
                                     MachineStateStorage machineStateStorage,
                                     ClockBackwardsSynchronizer clockBackwardsSynchronizer
    ) {
        super(machineStateStorage, clockBackwardsSynchronizer);
        this.machineCollection = machineCollection;
    }
    
    @Override
    protected MachineState distributeRemote(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
        MachineState machineState = machineCollection.distributeBySelf(namespace, instanceId, safeGuardDuration);
        if (machineState != null) {
            return machineState;
        }
        
        machineState = machineCollection.distributeByRevert(namespace, instanceId, safeGuardDuration);
        if (machineState != null) {
            return machineState;
        }
        return machineCollection.distribute(namespace, machineBit, instanceId);
    }
    
    
    @Override
    protected void revertRemote(String namespace, InstanceId instanceId, MachineState machineState) {
        machineCollection.revert(namespace, instanceId, machineState);
    }
    
    @Override
    protected void guardRemote(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) {
        machineCollection.guard(namespace, instanceId, machineState, safeGuardDuration);
    }
}
