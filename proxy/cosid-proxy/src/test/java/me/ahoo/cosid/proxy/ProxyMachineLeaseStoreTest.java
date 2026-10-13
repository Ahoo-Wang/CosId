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

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.lease.Lease;
import me.ahoo.cosid.machine.lease.MachineLeaseStore;
import me.ahoo.cosid.proxy.api.ClaimRequest;
import me.ahoo.cosid.proxy.api.LeaseDto;
import me.ahoo.cosid.proxy.api.LeaseResponse;
import me.ahoo.cosid.proxy.api.MachineLeaseApi;
import me.ahoo.cosid.proxy.api.ReleaseRequest;
import me.ahoo.cosid.proxy.api.RenewRequest;
import me.ahoo.cosid.test.machine.lease.InMemoryMachineLeaseStore;
import me.ahoo.cosid.test.machine.lease.MachineLeaseStoreSpec;

import java.util.Optional;

/**
 * Runs {@link MachineLeaseStoreSpec} through {@link ProxyMachineLeaseStore} and the wire types, on an API that passes
 * every call to an in-memory store the way the proxy server passes it to its backend.
 */
class ProxyMachineLeaseStoreTest extends MachineLeaseStoreSpec {
    private final ProxyMachineLeaseStore store = new ProxyMachineLeaseStore(new PassThroughLeaseApi(new InMemoryMachineLeaseStore()));
    
    @Override
    protected MachineLeaseStore getStore() {
        return store;
    }
    
    private static final class PassThroughLeaseApi implements MachineLeaseApi {
        private final MachineLeaseStore backend;
        
        private PassThroughLeaseApi(MachineLeaseStore backend) {
            this.backend = backend;
        }
        
        private static LeaseResponse response(Optional<Lease> lease) {
            return new LeaseResponse(lease.map(LeaseDto::of).orElse(null));
        }
        
        @Override
        public long serverTime() {
            return backend.serverTime();
        }
        
        @Override
        public LeaseResponse claim(String namespace, String kind, ClaimRequest request) {
            InstanceId instanceId = InstanceId.of(request.instanceId(), request.stable());
            return response(switch (kind) {
                case SELF -> backend.claimSelf(namespace, instanceId, request.now());
                case RELEASED -> backend.claimReleased(namespace, instanceId, request.maxMachineId(), request.now());
                case NEW -> backend.claimNew(namespace, instanceId, request.maxMachineId(), request.now());
                case EXPIRED -> backend.claimExpired(namespace, instanceId, request.maxMachineId(), request.expiredBefore(), request.now());
                default -> throw new IllegalArgumentException(kind);
            });
        }
        
        @Override
        public LeaseResponse renew(String namespace, RenewRequest request) {
            return response(backend.renew(request.lease().toLease(), request.now()));
        }
        
        @Override
        public boolean release(String namespace, ReleaseRequest request) {
            return backend.release(request.lease().toLease(), request.watermark());
        }
    }
}
