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

import com.google.errorprone.annotations.ThreadSafe;
import org.jspecify.annotations.NonNull;

/**
 * Machine state storage for persisting machine state across restarts.
 *
 * <p>Provides an interface for storing and retrieving machine state information,
 * which is essential for maintaining machine ID allocations in distributed
 * ID generation systems.
 *
 * @author ahoo wang
 */
@ThreadSafe
public interface MachineStateStorage {
    /**
     * Local machine state storage instance.
     */
    MachineStateStorage LOCAL = new LocalMachineStateStorage();
    /**
     * In-memory machine state storage instance.
     */
    MachineStateStorage IN_MEMORY = new InMemoryMachineStateStorage();

    /**
     * Gets the machine state for a given namespace and instance.
     *
     * @param namespace the namespace
     * @param instanceId the instance ID
     * @return the machine state, or NOT_FOUND if not found
     */
    @NonNull
    MachineState get(String namespace, InstanceId instanceId);

    /**
     * Sets the machine state for a given namespace and instance.
     *
     * @param namespace the namespace
     * @param machineId the machine ID
     * @param instanceId the instance ID
     */
    void set(String namespace, int machineId, InstanceId instanceId);

    /**
     * Removes the machine state for a given namespace and instance.
     *
     * @param namespace the namespace
     * @param instanceId the instance ID
     */
    void remove(String namespace, InstanceId instanceId);

    /**
     * Clears all machine states for a given namespace.
     *
     * @param namespace the namespace
     */
    void clear(String namespace);

    /**
     * Gets the number of machine states in a namespace.
     *
     * @param namespace the namespace
     * @return the number of machine states
     */
    int size(String namespace);

    /**
     * Checks if a machine state exists for a given namespace and instance.
     *
     * @param namespace the namespace
     * @param instanceId the instance ID
     * @return true if the state exists
     */
    boolean exists(String namespace, InstanceId instanceId);
}
