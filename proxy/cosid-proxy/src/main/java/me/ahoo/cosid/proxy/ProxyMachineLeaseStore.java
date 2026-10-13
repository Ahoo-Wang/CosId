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

package me.ahoo.cosid.proxy;

import me.ahoo.cosid.internal.Checks;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.lease.Lease;
import me.ahoo.cosid.machine.lease.MachineLeaseStore;
import me.ahoo.cosid.proxy.api.ClaimRequest;
import me.ahoo.cosid.proxy.api.LeaseDto;
import me.ahoo.cosid.proxy.api.LeaseResponse;
import me.ahoo.cosid.proxy.api.MachineLeaseApi;
import me.ahoo.cosid.proxy.api.ReleaseRequest;
import me.ahoo.cosid.proxy.api.RenewRequest;

import java.util.Optional;

/**
 * {@link MachineLeaseStore} through a CosId proxy server's version 2 lease API: each primitive is one HTTP call to the
 * server, which passes it to its backend store. The server clock is the backend store's.
 */
public class ProxyMachineLeaseStore implements MachineLeaseStore {
    private final MachineLeaseApi leaseApi;
    
    public ProxyMachineLeaseStore(MachineLeaseApi leaseApi) {
        this.leaseApi = Checks.checkNotNull(leaseApi, "leaseApi can not be null!");
    }
    
    private Optional<Lease> claim(String namespace, String kind, InstanceId instanceId, long now, int maxMachineId, long expiredBefore) {
        return toLease(leaseApi.claim(namespace, kind, new ClaimRequest(instanceId.getInstanceId(), instanceId.isStable(), now, maxMachineId,
            expiredBefore)));
    }
    
    private static Optional<Lease> toLease(LeaseResponse response) {
        if (response == null) {
            return Optional.empty();
        }
        LeaseDto lease = response.lease();
        return lease == null ? Optional.empty() : Optional.of(lease.toLease());
    }
    
    @Override
    public Optional<Lease> claimSelf(String namespace, InstanceId instanceId, long now) {
        return claim(namespace, MachineLeaseApi.SELF, instanceId, now, 0, 0);
    }
    
    @Override
    public Optional<Lease> claimReleased(String namespace, InstanceId instanceId, int maxMachineId, long now) {
        return claim(namespace, MachineLeaseApi.RELEASED, instanceId, now, maxMachineId, 0);
    }
    
    @Override
    public Optional<Lease> claimNew(String namespace, InstanceId instanceId, int maxMachineId, long now) {
        return claim(namespace, MachineLeaseApi.NEW, instanceId, now, maxMachineId, 0);
    }
    
    @Override
    public Optional<Lease> claimExpired(String namespace, InstanceId instanceId, int maxMachineId, long expiredBefore, long now) {
        return claim(namespace, MachineLeaseApi.EXPIRED, instanceId, now, maxMachineId, expiredBefore);
    }
    
    @Override
    public Optional<Lease> renew(Lease lease, long now) {
        return toLease(leaseApi.renew(lease.getNamespace(), new RenewRequest(LeaseDto.of(lease), now)));
    }
    
    @Override
    public boolean release(Lease lease, long watermark) {
        return leaseApi.release(lease.getNamespace(), new ReleaseRequest(LeaseDto.of(lease), watermark));
    }
    
    /**
     * The backend store's clock, through the server.
     */
    @Override
    public long serverTime() {
        return leaseApi.serverTime();
    }
    
    /**
     * The backend store's clock for the namespace, through the server (a server before 4.1 answers with its one clock).
     */
    @Override
    public long serverTime(String namespace) {
        return leaseApi.serverTime(namespace);
    }
}
