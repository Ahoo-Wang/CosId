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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author ahoo wang
 */
class LocalMachineStateStorageTest {
    private static final String namespace = "test";
    private final LocalMachineStateStorage fileLocalMachineState = new LocalMachineStateStorage();

    @Test
    void get() {
        fileLocalMachineState.remove(namespace, InstanceId.NONE);
        MachineState machineState = fileLocalMachineState.get(namespace, InstanceId.NONE);
        Assertions.assertEquals(-1, machineState.getMachineId());

        fileLocalMachineState.set(namespace, 1, InstanceId.NONE);
        machineState = fileLocalMachineState.get(namespace, InstanceId.NONE);
        Assertions.assertEquals(1, machineState.getMachineId());
    }

    @Test
    void set() {
        fileLocalMachineState.set(namespace, 1, InstanceId.NONE);
        MachineState machineState = fileLocalMachineState.get(namespace, InstanceId.NONE);
        Assertions.assertEquals(1, machineState.getMachineId());
        fileLocalMachineState.set(namespace, 2, InstanceId.NONE);
        machineState = fileLocalMachineState.get(namespace, InstanceId.NONE);
        Assertions.assertEquals(2, machineState.getMachineId());
    }

    @Test
    void remove() {
        fileLocalMachineState.remove(namespace, InstanceId.NONE);
        Assertions.assertTrue(!fileLocalMachineState.exists(namespace, InstanceId.NONE));
    }

    @Test
    void clear() {
        fileLocalMachineState.clear(namespace);
        fileLocalMachineState.set(namespace, 1, InstanceId.of("test", false));
        fileLocalMachineState.set(namespace, 2, InstanceId.of("test1", false));
        fileLocalMachineState.clear(namespace);
        Assertions.assertEquals(0, fileLocalMachineState.size(namespace));
    }
}
