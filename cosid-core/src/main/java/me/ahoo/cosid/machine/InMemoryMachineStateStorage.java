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
import jakarta.annotation.Nonnull;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class InMemoryMachineStateStorage implements MachineStateStorage {
    private final ConcurrentHashMap<NamespacedInstanceId, MachineState> states = new ConcurrentHashMap<>();
    
    @Nonnull
    @Override
    public MachineState get(String namespace, InstanceId instanceId) {
        return states.getOrDefault(new NamespacedInstanceId(namespace, instanceId), MachineState.NOT_FOUND);
    }
    
    @Override
    public void set(String namespace, int machineId, InstanceId instanceId) {
        NamespacedInstanceId namespacedInstanceId = new NamespacedInstanceId(namespace, instanceId);
        MachineState machineState = MachineState.of(machineId, System.currentTimeMillis());
        if (log.isDebugEnabled()) {
            log.debug("Set [{}] to [{}].", namespacedInstanceId, machineState);
        }
        states.put(namespacedInstanceId, machineState);
    }
    
    @Override
    public void remove(String namespace, InstanceId instanceId) {
        NamespacedInstanceId namespacedInstanceId = new NamespacedInstanceId(namespace, instanceId);
        if (log.isInfoEnabled()) {
            log.info("Remove : [{}].", namespace);
        }
        states.remove(namespacedInstanceId);
    }
    
    @Override
    public void clear(String namespace) {
        if (log.isInfoEnabled()) {
            log.info("Clear namespace : [{}].", namespace);
        }
        states.clear();
    }
    
    @Override
    public int size(String namespace) {
        return states.size();
    }
    
    @Override
    public boolean exists(String namespace, InstanceId instanceId) {
        return states.containsKey(new NamespacedInstanceId(namespace, instanceId));
    }
}
