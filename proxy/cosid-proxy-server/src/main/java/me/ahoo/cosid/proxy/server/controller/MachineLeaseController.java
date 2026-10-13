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

package me.ahoo.cosid.proxy.server.controller;

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
import me.ahoo.cosid.proxy.server.configuration.ProxyServerProperties;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Optional;

/**
 * Machine id leases, version 2: each call goes straight to the backend {@link MachineLeaseStore}, so the server keeps
 * no state and can run as several nodes behind a load balancer.
 *
 * <p>Times sent by clients must come from {@link #serverTime()}: a time more than {@code cosid.proxy.server.max-clock-skew}
 * ahead of the store's clock is rejected, since it would keep a machine id from ever expiring, and an expired claim's
 * cut-off must be at least {@code cosid.proxy.server.min-safe-guard-duration} behind it, so no client takes over a live
 * lease.</p>
 */
@RestController
public class MachineLeaseController implements MachineLeaseApi {
    private static final int MAX_MACHINE_ID = Integer.MAX_VALUE - 1;
    private final MachineLeaseStore store;
    private final Duration maxClockSkew;
    private final Duration minSafeGuardDuration;
    
    public MachineLeaseController(MachineLeaseStore store) {
        this(store, new ProxyServerProperties());
    }
    
    @Autowired
    public MachineLeaseController(MachineLeaseStore store, ProxyServerProperties proxyServerProperties) {
        this.store = store;
        this.maxClockSkew = proxyServerProperties.getMaxClockSkew();
        this.minSafeGuardDuration = proxyServerProperties.getMinSafeGuardDuration();
    }
    
    @Override
    @Operation(summary = "The backend store's clock, in milliseconds.")
    public long serverTime() {
        return store.serverTime();
    }
    
    @Override
    @Operation(summary = "Claim a machine id: self, released, new or expired.")
    public LeaseResponse claim(@PathVariable String namespace, @PathVariable String kind, @RequestBody ClaimRequest request) {
        checkNamespace(namespace);
        Checks.checkArgument(!Checks.isNullOrEmpty(request.instanceId()) && !request.instanceId().isBlank(), "instanceId can not be blank!");
        checkNotAhead(namespace, "now", request.now());
        InstanceId instanceId = InstanceId.of(request.instanceId(), request.stable());
        Optional<Lease> lease = switch (kind) {
            case SELF -> store.claimSelf(namespace, instanceId, request.now());
            case RELEASED -> store.claimReleased(namespace, instanceId, checkMaxMachineId(request), request.now());
            case NEW -> store.claimNew(namespace, instanceId, checkMaxMachineId(request), request.now());
            case EXPIRED -> store.claimExpired(namespace, instanceId, checkMaxMachineId(request), checkExpiredBefore(namespace, request.expiredBefore()),
                request.now());
            default -> throw new IllegalArgumentException("Unknown claim:[" + kind + "], expected self, released, new or expired!");
        };
        return response(lease);
    }
    
    @Override
    @Operation(summary = "Renew a lease, if it is still at its version.")
    public LeaseResponse renew(@PathVariable String namespace, @RequestBody RenewRequest request) {
        checkNotAhead(namespace, "now", request.now());
        return response(store.renew(lease(namespace, request.lease()), request.now()));
    }
    
    @Override
    @Operation(summary = "Release a lease on shutdown, if it is still at its version.")
    public boolean release(@PathVariable String namespace, @RequestBody ReleaseRequest request) {
        checkNotAhead(namespace, "watermark", request.watermark());
        return store.release(lease(namespace, request.lease()), request.watermark());
    }
    
    private static LeaseResponse response(Optional<Lease> lease) {
        return new LeaseResponse(lease.map(LeaseDto::of).orElse(null));
    }
    
    private static Lease lease(String namespace, LeaseDto lease) {
        checkNamespace(namespace);
        Checks.checkArgument(lease != null, "lease can not be null!");
        Checks.checkArgument(namespace.equals(lease.namespace()), "lease namespace:[%s] does not match the path:[%s]!", lease.namespace(), namespace);
        return lease.toLease();
    }
    
    /**
     * Only leases renewed at least {@code min-safe-guard-duration} ago, by the store's clock, may be taken over.
     */
    private long checkExpiredBefore(String namespace, long expiredBefore) {
        long latest = store.serverTime(namespace) - minSafeGuardDuration.toMillis();
        Checks.checkArgument(expiredBefore <= latest, "expiredBefore:[%s] is later than the store clock minus the minimum safe guard duration [%s]!",
            expiredBefore, minSafeGuardDuration);
        return expiredBefore;
    }
    
    private static int checkMaxMachineId(ClaimRequest request) {
        Checks.checkArgument(request.maxMachineId() >= 0 && request.maxMachineId() <= MAX_MACHINE_ID,
            "maxMachineId:[%s] must be in [0, %s]!", request.maxMachineId(), MAX_MACHINE_ID);
        return request.maxMachineId();
    }
    
    private static void checkNamespace(String namespace) {
        Checks.checkArgument(!Checks.isNullOrEmpty(namespace) && !namespace.isBlank(), "namespace can not be blank!");
    }
    
    /**
     * A time far ahead of the store would keep a machine id from ever being recycled, or make its next owner wait.
     */
    private void checkNotAhead(String namespace, String name, long time) {
        if (maxClockSkew == null || maxClockSkew.isZero() || maxClockSkew.isNegative()) {
            return;
        }
        long latest = store.serverTime(namespace) + maxClockSkew.toMillis();
        Checks.checkArgument(time <= latest, "%s:[%s] is more than [%s] ahead of the store clock!", name, time, maxClockSkew);
    }
}
