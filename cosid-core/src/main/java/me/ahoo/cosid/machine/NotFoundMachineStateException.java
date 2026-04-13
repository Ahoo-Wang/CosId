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

import me.ahoo.cosid.CosIdException;

import com.google.common.base.Strings;

/**
 * Exception thrown when machine state is not found.
 *
 * <p>Indicates that the machine state for a specific instance
 * could not be found in the distributed store.
 *
 * @author ahoo wang
 */
public class NotFoundMachineStateException extends CosIdException {
    private final String namespace;
    private final InstanceId instanceId;

    /**
     * Creates a new exception.
     *
     * @param namespace  the namespace
     * @param instanceId the instance ID
     */
    public NotFoundMachineStateException(String namespace, InstanceId instanceId) {
        super(Strings.lenientFormat("Not found the MachineState of instance[%s]@[%s]!", instanceId, namespace));
        this.namespace = namespace;
        this.instanceId = instanceId;
    }

    /**
     * Gets the namespace.
     *
     * @return the namespace
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Gets the instance ID.
     *
     * @return the instance ID
     */
    public InstanceId getInstanceId() {
        return instanceId;
    }
}
