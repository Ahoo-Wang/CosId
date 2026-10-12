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

package me.ahoo.cosid.proxy.api;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.lease.Lease;

/**
 * A machine id lease on the wire.
 *
 * @param namespace  the namespace
 * @param instanceId the instance holding the machine id
 * @param stable     whether the instance is stable
 * @param machineId  the machine id
 * @param watermark  the last timestamp the machine id may have been used for
 * @param renewedAt  the store time of the last claim or renewal
 * @param version    the token that changes on every write
 */
public record LeaseDto(String namespace, String instanceId, boolean stable, int machineId, long watermark, long renewedAt, long version) {
    
    public static LeaseDto of(Lease lease) {
        return new LeaseDto(lease.getNamespace(), lease.getInstanceId().getInstanceId(), lease.isStable(), lease.getMachineId(),
            lease.getWatermark(), lease.getRenewedAt(), lease.getVersion());
    }
    
    public Lease toLease() {
        return new Lease(namespace, InstanceId.of(instanceId, stable), machineId, watermark, renewedAt, version);
    }
}
