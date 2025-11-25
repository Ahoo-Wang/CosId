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

import com.google.common.annotations.Beta;

import java.util.Map;

/**
 * MachineId Guarder.
 *
 * <p>The MachineIdGuarder interface provides mechanisms to guard and manage machine IDs in a distributed system.
 * It allows registering and unregistering instances within namespaces, and controlling the lifecycle of the guarder.
 *
 * <p>This interface is designed to prevent machine ID conflicts and ensure unique identification across distributed instances.
 *
 * <p>Example usage:
 * <pre>{@code
 * MachineIdGuarder guarder = new SomeMachineIdGuarder();
 * guarder.register("myNamespace", new InstanceId("instance1"));
 * guarder.start();
 * // ... use the guarder
 * guarder.stop();
 * }</pre>
 *
 * @author ahoo wang
 */
@Beta
public interface MachineIdGuarder {
    MachineIdGuarder NONE = new MachineIdGuarder.None();

    /**
     * Gets the guardian status for all registered instances.
     *
     * <p>This method returns a map of namespaced instance IDs to their current guardian status,
     * indicating whether the guarding operation was successful or failed for each instance.
     *
     * @return a map of namespaced instance IDs to their guardian status
     */
    Map<NamespacedInstanceId, GuardianState> getGuardianStates();

    /**
     * Checks if any registered instance has failed guarding.
     *
     * <p>This method returns true if at least one registered instance has a guardian status of FAILURE,
     * indicating that the guarding operation failed for that instance.
     *
     * @return true if any instance has failed guarding, false otherwise
     */
    default boolean hasFailure() {
        return getGuardianStates().values().stream().anyMatch(GuardianState::isFailed);
    }

    /**
     * Registers an instance ID within a specific namespace.
     *
     * <p>This method associates the given instance ID with the provided namespace, allowing the guarder
     * to track and manage machine IDs for conflict prevention.
     *
     * @param namespace  the namespace to register the instance in, must not be null
     * @param instanceId the instance ID to register, must not be null
     * @throws IllegalArgumentException if namespace or instanceId is null
     */
    void register(String namespace, InstanceId instanceId);

    /**
     * Unregisters an instance ID from a specific namespace.
     *
     * <p>This method removes the association of the given instance ID with the provided namespace,
     * releasing any resources or locks held for that instance.
     *
     * @param namespace  the namespace to unregister the instance from, must not be null
     * @param instanceId the instance ID to unregister, must not be null
     * @throws IllegalArgumentException if namespace or instanceId is null
     */
    void unregister(String namespace, InstanceId instanceId);

    /**
     * Starts the machine ID guarder.
     *
     * <p>This method initializes the guarder and begins monitoring or guarding machine IDs.
     * After calling start(), the guarder is considered running.
     *
     * @throws IllegalStateException if the guarder is already running
     */
    void start();

    /**
     * Stops the machine ID guarder.
     *
     * <p>This method shuts down the guarder and stops monitoring or guarding machine IDs.
     * After calling stop(), the guarder is no longer running.
     *
     * @throws IllegalStateException if the guarder is not running
     */
    void stop();

    /**
     * Checks if the machine ID guarder is currently running.
     *
     * @return true if the guarder is running, false otherwise
     */
    boolean isRunning();

    /**
     * A no-operation implementation of MachineIdGuarder.
     *
     * <p>This implementation provides empty methods that do nothing, useful for testing or when no guarding is needed.
     */
    class None implements MachineIdGuarder {

        /**
         * {@inheritDoc}
         *
         * <p>This implementation returns an empty map since no instances are registered.
         */
        @Override
        public Map<NamespacedInstanceId, GuardianState> getGuardianStates() {
            return Map.of();
        }

        @Override
        public void register(String namespace, InstanceId instanceId) {

        }

        @Override
        public void unregister(String namespace, InstanceId instanceId) {

        }

        @Override
        public void start() {

        }

        @Override
        public void stop() {

        }

        @Override
        public boolean isRunning() {
            return false;
        }
    }
}
