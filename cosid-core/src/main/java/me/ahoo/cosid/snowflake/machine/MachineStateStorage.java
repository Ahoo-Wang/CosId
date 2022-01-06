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

import javax.annotation.concurrent.ThreadSafe;

/**
 * 机器状态的本地缓存
 *
 * @author ahoo wang
 */
@ThreadSafe
public interface MachineStateStorage {
    MachineStateStorage LOCAL = new LocalMachineStateStorage();
    MachineStateStorage NONE = new None();

    MachineState get(String namespace, InstanceId instanceId);

    void set(String namespace, int machineId, InstanceId instanceId);

    void remove(String namespace, InstanceId instanceId);

    void clear(String namespace);

    int size(String namespace);

    boolean exists(String namespace, InstanceId instanceId);

    class None implements MachineStateStorage {
        @Override
        public MachineState get(String namespace, InstanceId instanceId) {
            return MachineState.NOT_FOUND;
        }

        @Override
        public void set(String namespace, int machineId, InstanceId instanceId) {

        }

        @Override
        public void remove(String namespace, InstanceId instanceId) {

        }

        @Override
        public void clear(String namespace) {

        }

        @Override
        public int size(String namespace) {
            return 0;
        }


        @Override
        public boolean exists(String namespace, InstanceId instanceId) {
            return false;
        }
    }
}
