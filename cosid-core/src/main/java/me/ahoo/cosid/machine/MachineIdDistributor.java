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

import com.google.common.base.Strings;
import com.google.errorprone.annotations.ThreadSafe;

import java.time.Duration;

/**
 * Machine ID distributor for coordinating unique machine identifiers in distributed systems.
 *
 * <p>In distributed ID generation, each machine or instance must have a unique identifier
 * to ensure global uniqueness of generated IDs. This interface provides the contract for
 * distributing and managing these machine IDs across a cluster.
 *
 * <p>The distributor is responsible for:
 * <ul>
 *   <li>Allocating unique machine IDs within a namespace</li>
 *   <li>Reverting (releasing) machine IDs when instances shut down</li>
 *   <li>Guarding machine IDs with heartbeats to detect failures</li>
 * </ul>
 *
 * <p>Common implementations include:
 * <ul>
 *   <li>Redis-based distribution</li>
 *   <li>ZooKeeper-based distribution</li>
 *   <li>JDBC-based distribution</li>
 * </ul>
 *
 * <p><img src="../../doc-files/Machine-Id-Safe-Guard.png" alt="Machine-Id-Safe-Guard"></p>
 *
 * <p>Implementations of this interface are expected to be thread-safe and can be
 * used concurrently across multiple threads.
 *
 * @author ahoo wang
 */
@ThreadSafe
public interface MachineIdDistributor extends MachineIdDistribute {
    /**
     * A duration representing forever for safe guard purposes.
     *
     * <p>This constant is used to indicate that machine ID guarding should
     * persist indefinitely, without expiration.
     */
    Duration FOREVER_SAFE_GUARD_DURATION = Duration.ofMillis(Long.MAX_VALUE);

    /**
     * Calculate the maximum machine ID for the specified bit size.
     *
     * <p>This method calculates the largest machine ID that can be represented
     * with the given number of bits, which determines how many unique machines
     * can participate in ID generation.
     *
     * @param machineBit The number of bits used for machine IDs
     * @return The maximum machine ID value
     */
    static int maxMachineId(int machineBit) {
        return ~(-1 << machineBit);
    }

    /**
     * Calculate the total number of machine IDs for the specified bit size.
     *
     * <p>This method calculates the total number of unique machine IDs that
     * can be allocated with the given bit size, including zero.
     *
     * @param machineBit The number of bits used for machine IDs
     * @return The total number of machine IDs
     */
    static int totalMachineIds(int machineBit) {
        return maxMachineId(machineBit) + 1;
    }

    /**
     * Generate a namespaced machine ID string.
     *
     * <p>This method creates a formatted string that combines a namespace
     * with a machine ID, padding the machine ID with leading zeros to
     * ensure consistent formatting.
     *
     * @param namespace The namespace for the machine ID
     * @param machineId The machine ID
     * @return A formatted namespaced machine ID string
     */
    static String namespacedMachineId(String namespace, int machineId) {
        return namespace + "." + Strings.padStart(String.valueOf(machineId), 8, '0');
    }

    /**
     * Calculate the safe guard timestamp.
     *
     * <p>This method calculates the timestamp before which a machine ID
     * should be considered stale and potentially reallocated if not
     * guarded by a heartbeat.
     *
     * @param safeGuardDuration The duration for safe guarding
     * @param stable            Whether the system is in a stable state
     * @return The safe guard timestamp
     */
    static long getSafeGuardAt(Duration safeGuardDuration, boolean stable) {
        if (stable) {
            return 0L;
        }

        if (FOREVER_SAFE_GUARD_DURATION.equals(safeGuardDuration)) {
            return 0L;
        }

        long safeGuardAt = System.currentTimeMillis() - safeGuardDuration.toMillis();
        if (safeGuardAt < 0) {
            return 0L;
        }
        return safeGuardAt;
    }

    /**
     * Revert (release) a previously allocated machine ID.
     *
     * <p>This method releases a machine ID back to the distributor, making
     * it available for reallocation. This should be called when an instance
     * shuts down gracefully to enable efficient reuse of machine IDs.
     *
     * @param namespace  The namespace of the machine ID
     * @param instanceId The instance identifier whose machine ID should be released
     * @throws NotFoundMachineStateException if the machine ID was not found
     */
    void revert(String namespace, InstanceId instanceId) throws NotFoundMachineStateException;

    /**
     * Guard a machine ID with a heartbeat mechanism.
     *
     * <p>This method updates the heartbeat timestamp for a machine ID to
     * prevent it from being considered stale and reallocated. It should
     * be called periodically by instances to maintain their machine ID lease.
     *
     * <p><img src="../../doc-files/Machine-Id-Safe-Guard.png" alt="Machine-Id-Safe-Guard"></p>
     *
     * @param namespace         The namespace of the machine ID
     * @param instanceId        The instance identifier whose machine ID should be guarded
     * @param safeGuardDuration The duration for safe guarding the allocation
     * @throws NotFoundMachineStateException if the machine ID was not found
     * @throws MachineIdLostException        if the machine ID has been lost or reallocated
     */
    void guard(String namespace, InstanceId instanceId, Duration safeGuardDuration) throws NotFoundMachineStateException, MachineIdLostException;

}
