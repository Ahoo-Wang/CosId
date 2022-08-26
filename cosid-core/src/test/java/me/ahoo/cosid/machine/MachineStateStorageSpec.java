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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public abstract class MachineStateStorageSpec {
    private static final String namespace = "{test}";
    private final MachineStateStorage machineStateStorage = createMachineStateStorage();
    
    abstract MachineStateStorage createMachineStateStorage();
    
    @Test
    void get() {
        machineStateStorage.remove(namespace, InstanceId.NONE);
        MachineState machineState = machineStateStorage.get(namespace, InstanceId.NONE);
        Assertions.assertEquals(-1, machineState.getMachineId());
        
        machineStateStorage.set(namespace, 1, InstanceId.NONE);
        machineState = machineStateStorage.get(namespace, InstanceId.NONE);
        Assertions.assertEquals(1, machineState.getMachineId());
    }
    
    @Test
    void set() {
        machineStateStorage.set(namespace, 1, InstanceId.NONE);
        MachineState machineState = machineStateStorage.get(namespace, InstanceId.NONE);
        Assertions.assertEquals(1, machineState.getMachineId());
        machineStateStorage.set(namespace, 2, InstanceId.NONE);
        machineState = machineStateStorage.get(namespace, InstanceId.NONE);
        Assertions.assertEquals(2, machineState.getMachineId());
    }
    
    @Test
    void remove() {
        machineStateStorage.remove(namespace, InstanceId.NONE);
        Assertions.assertFalse(machineStateStorage.exists(namespace, InstanceId.NONE));
    }
    
    @Test
    void clear() {
        machineStateStorage.clear(namespace);
        machineStateStorage.set(namespace, 1, InstanceId.of("test", false));
        machineStateStorage.set(namespace, 2, InstanceId.of("test1", false));
        machineStateStorage.clear(namespace);
        Assertions.assertEquals(0, machineStateStorage.size(namespace));
    }
}
