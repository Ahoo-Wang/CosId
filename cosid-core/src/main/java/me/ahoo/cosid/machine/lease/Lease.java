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

import me.ahoo.cosid.internal.Checks;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineState;

import com.google.errorprone.annotations.Immutable;

import java.util.Objects;

/**
 * A machine id held by an instance, as stored by a {@link MachineLeaseStore}.
 *
 * <p>A lease keeps three separate values:</p>
 * <ul>
 *   <li>{@link #getWatermark() watermark}: the last timestamp the machine id may have been used for, which the next
 *   owner must not go back to (its clock-backwards watermark). It never decreases, and renewing does not move it.</li>
 *   <li>{@link #getRenewedAt() renewed at}: the store time of the last claim or renewal, which decides expiry.</li>
 *   <li>{@link #getVersion() version}: an opaque token that changes on every write.
 *   {@link MachineLeaseStore#renew} and {@link MachineLeaseStore#release} only succeed with the current version, so an
 *   owner whose lease was taken over in between finds out instead of overwriting the new owner.</li>
 * </ul>
 *
 * @author ahoo wang
 * @since 3.4.0
 */
@Immutable
public final class Lease {
    private final String namespace;
    private final InstanceId instanceId;
    private final int machineId;
    private final long watermark;
    private final long renewedAt;
    private final long version;
    
    /**
     * Create a lease.
     *
     * @param watermark the last timestamp the machine id may have been used for
     * @param renewedAt the store time of the last claim or renewal
     * @param version   the token that changes on every write
     */
    public Lease(String namespace, InstanceId instanceId, int machineId, long watermark, long renewedAt, long version) {
        Checks.checkNotNull(namespace, "namespace can not be null!");
        Checks.checkNotNull(instanceId, "instanceId can not be null!");
        Checks.checkArgument(machineId >= 0, "machineId:[%s] must be greater than or equal to 0!", machineId);
        this.namespace = namespace;
        this.instanceId = instanceId;
        this.machineId = machineId;
        this.watermark = watermark;
        this.renewedAt = renewedAt;
        this.version = version;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public InstanceId getInstanceId() {
        return instanceId;
    }
    
    public boolean isStable() {
        return instanceId.isStable();
    }
    
    public int getMachineId() {
        return machineId;
    }
    
    /**
     * The last timestamp the machine id may have been used for: the owner waits until its clock passes it.
     */
    public long getWatermark() {
        return watermark;
    }
    
    /**
     * The store time of the last claim or renewal: the lease expires a safe-guard duration after it.
     */
    public long getRenewedAt() {
        return renewedAt;
    }
    
    public long getVersion() {
        return version;
    }
    
    public MachineState toMachineState() {
        return MachineState.of(machineId, watermark);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Lease that)) {
            return false;
        }
        return machineId == that.machineId && watermark == that.watermark && renewedAt == that.renewedAt && version == that.version
            && namespace.equals(that.namespace) && instanceId.equals(that.instanceId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(namespace, instanceId, machineId, watermark, renewedAt, version);
    }
    
    @Override
    public String toString() {
        return "Lease{"
            + "namespace='" + namespace + '\''
            + ", instanceId=" + instanceId
            + ", machineId=" + machineId
            + ", watermark=" + watermark
            + ", renewedAt=" + renewedAt
            + ", version=" + version
            + '}';
    }
}
