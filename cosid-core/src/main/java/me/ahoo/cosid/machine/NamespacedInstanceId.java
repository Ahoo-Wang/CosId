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

import me.ahoo.cosid.internal.Checks;

import java.util.Objects;

/**
 * Combines namespace with instance ID for unique identification.
 *
 * <p>Used to ensure machine IDs are unique across different
 * namespaces/business domains.
 *
 * @author ahoo wang
 */
public class NamespacedInstanceId {
    private final String namespace;

    private final InstanceId instanceId;

    /**
     * Creates a new namespaced instance ID.
     *
     * @param namespace  the namespace
     * @param instanceId the instance ID
     */
    public NamespacedInstanceId(String namespace, InstanceId instanceId) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NamespacedInstanceId)) {
            return false;
        }
        NamespacedInstanceId that = (NamespacedInstanceId) o;
        return Objects.equals(getNamespace(), that.getNamespace()) && Objects.equals(getInstanceId(), that.getInstanceId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getNamespace(), getInstanceId());
    }

    @Override
    public String toString() {
        return Checks.toString(this, "namespace", namespace, "instanceId", instanceId);
    }
}
