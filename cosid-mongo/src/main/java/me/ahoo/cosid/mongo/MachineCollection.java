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

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineState;

import java.time.Duration;

public interface MachineCollection {
    String COLLECTION_NAME = "cosid_machine";
    
    int nextMachineId(String namespace);
    
    MachineState distribute(String namespace, int machineBit, InstanceId instanceId);
    
    MachineState distributeByRevert(String namespace, InstanceId instanceId, Duration safeGuardDuration);
    
    MachineState distributeBySelf(String namespace, InstanceId instanceId, Duration safeGuardDuration);
    
    void revert(String namespace, InstanceId instanceId, MachineState machineState) throws MachineIdLostException;
    
    void guard(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) throws MachineIdLostException;
}
