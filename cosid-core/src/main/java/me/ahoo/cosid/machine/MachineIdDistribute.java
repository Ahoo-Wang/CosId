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

import org.jspecify.annotations.NonNull;

import java.time.Duration;

public interface MachineIdDistribute {
    /**
     * Distribute (allocate) a machine ID within the specified namespace.
     *
     * <p>This method allocates a unique machine ID for the given instance
     * within the specified namespace. The allocated ID is guaranteed to
     * be unique within that namespace for the duration of its lease.
     *
     * <p>The method returns a {@link MachineState} object containing the
     * allocated machine ID and associated metadata.
     *
     * @param namespace         The namespace for machine ID allocation
     * @param machineBit        The number of bits to use for machine IDs
     * @param instanceId        The instance identifier requesting the machine ID
     * @param safeGuardDuration The duration for safe guarding the allocation
     * @return The machine state containing the allocated machine ID
     * @throws MachineIdOverflowException if no more machine IDs are available
     */
    @NonNull
    MachineState distribute(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) throws MachineIdOverflowException;
}
