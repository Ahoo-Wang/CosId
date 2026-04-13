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

import com.google.common.base.Objects;
import com.google.errorprone.annotations.Immutable;

/**
 * Logical machine identifier for distributed ID generation.
 *
 * <p>This represents a logical machine ID that is not necessarily tied to a physical
 * or virtual machine. It provides uniqueness across different processes/services
 * which are isolated using namespaces.
 *
 * @author ahoo wang
 * @see InstanceId
 */
@Immutable
public class MachineId {
    private final int machineId;

    /**
     * Creates a new MachineId.
     *
     * @param machineId the machine ID value
     */
    public MachineId(int machineId) {
        this.machineId = machineId;
    }

    /**
     * Gets the machine ID value.
     *
     * @return the machine ID
     */
    public int getMachineId() {
        return machineId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MachineId)) {
            return false;
        }
        MachineId machineId1 = (MachineId) o;
        return getMachineId() == machineId1.getMachineId();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getMachineId());
    }
}
