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

package me.ahoo.cosid.machine.lease;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineState;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.Immutable;

import java.util.Objects;

/**
 * A machine id held by an instance, as stored by a {@link MachineLeaseStore}.
 *
 * <p>{@code version} changes on every write to the lease. {@link MachineLeaseStore#renew} and
 * {@link MachineLeaseStore#release} only succeed with the current version, so an owner whose lease was taken over in
 * between finds out instead of overwriting the new owner.</p>
 *
 * @author ahoo wang
 * @since 3.4.0
 */
@Immutable
public final class Lease {
    private final String namespace;
    private final InstanceId instanceId;
    private final int machineId;
    private final long lastTimestamp;
    private final long version;
    
    public Lease(String namespace, InstanceId instanceId, int machineId, long lastTimestamp, long version) {
        Preconditions.checkNotNull(namespace, "namespace can not be null!");
        Preconditions.checkNotNull(instanceId, "instanceId can not be null!");
        Preconditions.checkArgument(machineId >= 0, "machineId:[%s] must be greater than or equal to 0!", machineId);
        this.namespace = namespace;
        this.instanceId = instanceId;
        this.machineId = machineId;
        this.lastTimestamp = lastTimestamp;
        this.version = version;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public InstanceId getInstanceId() {
        return instanceId;
    }
    
    /**
     * Whether the machine id is never recycled by other instances, see {@link InstanceId#isStable()}.
     */
    public boolean isStable() {
        return instanceId.isStable();
    }
    
    public int getMachineId() {
        return machineId;
    }
    
    /**
     * The last time, in milliseconds, the owner renewed the lease: other instances may take the machine id over once it is
     * older than the safe guard duration (unless the lease is stable).
     */
    public long getLastTimestamp() {
        return lastTimestamp;
    }
    
    public long getVersion() {
        return version;
    }
    
    public MachineState toMachineState() {
        return MachineState.of(machineId, lastTimestamp);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Lease)) {
            return false;
        }
        Lease that = (Lease) o;
        return machineId == that.machineId && lastTimestamp == that.lastTimestamp && version == that.version
            && namespace.equals(that.namespace) && instanceId.equals(that.instanceId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(namespace, instanceId, machineId, lastTimestamp, version);
    }
    
    @Override
    public String toString() {
        return "Lease{"
            + "namespace='" + namespace + '\''
            + ", instanceId=" + instanceId
            + ", machineId=" + machineId
            + ", lastTimestamp=" + lastTimestamp
            + ", version=" + version
            + '}';
    }
}
