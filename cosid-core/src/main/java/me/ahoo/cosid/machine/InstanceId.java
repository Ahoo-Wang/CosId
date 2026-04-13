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

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.errorprone.annotations.Immutable;

/**
 * Represents a specific deployment instance of a service.
 *
 * <p>An InstanceId identifies a particular running instance of a service, which may
 * be part of a deployment that provides stable machine IDs (stable=true) or a
 * dynamic instance where machine IDs may change (stable=false).
 *
 * @author ahoo wang
 * @see MachineId
 */
@Immutable
public class InstanceId {
    /**
     * Sentinel value representing no instance.
     */
    public static final InstanceId NONE = new InstanceId("none", false);

    private final String instanceId;
    private final boolean stable;

    /**
     * Creates a new InstanceId.
     *
     * @param instanceId the instance identifier string
     * @param stable    whether this instance has a stable identity
     */
    public InstanceId(String instanceId, boolean stable) {
        this.instanceId = instanceId;
        this.stable = stable;
    }

    /**
     * Checks if this instance has a stable identity.
     *
     * <p>Stable instances (stable=true) are deployed with stable identities (e.g., Kubernetes StatefulSet)
     * and can rely on having consistent machine IDs across restarts.
     *
     * @return true if the instance has a stable identity
     */
    public boolean isStable() {
        return stable;
    }

    /**
     * Gets the instance identifier string.
     *
     * @return the instance ID
     */
    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("instanceId", instanceId)
            .add("stable", stable)
            .toString();
    }

    /**
     * Creates an InstanceId from host and port.
     *
     * @param host   the host address
     * @param port   the port number
     * @param stable whether this instance has a stable identity
     * @return a new InstanceId
     */
    public static InstanceId of(String host, int port, boolean stable) {
        String instanceIdStr = String.format("%s:%s", host, port);
        return of(instanceIdStr, stable);
    }

    /**
     * Creates an InstanceId from an instance ID string.
     *
     * @param instanceId the instance identifier
     * @param stable    whether this instance has a stable identity
     * @return a new InstanceId
     */
    public static InstanceId of(String instanceId, boolean stable) {
        return new InstanceId(instanceId, stable);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InstanceId)) {
            return false;
        }
        InstanceId that = (InstanceId) o;
        return stable == that.stable && Objects.equal(instanceId, that.instanceId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(instanceId, stable);
    }
}
