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

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Machine State Storage.
 *
 * @author ahoo wang
 */
@ThreadSafe
public interface MachineStateStorage {
    MachineStateStorage LOCAL = new LocalMachineStateStorage();
    MachineStateStorage IN_MEMORY = new InMemoryMachineStateStorage();
    @Nonnull
    MachineState get(String namespace, InstanceId instanceId);
    
    void set(String namespace, int machineId, InstanceId instanceId);
    
    void remove(String namespace, InstanceId instanceId);
    
    void clear(String namespace);
    
    int size(String namespace);
    
    boolean exists(String namespace, InstanceId instanceId);
}
