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

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Machine id leases, version 2: the {@code MachineLeaseStore} primitives of the server's backend, passed through.
 *
 * <p>Clients run the allocation algorithm themselves ({@code MachineLeaseManager}) on a {@code ProxyMachineLeaseStore}.
 * Every {@code now} and {@code expiredBefore} must come from {@link #serverTime()}, the backend store's clock.</p>
 */
@HttpExchange("machines/v2")
public interface MachineLeaseApi {
    /**
     * The claim kinds, as path segments.
     */
    String SELF = "self";
    String RELEASED = "released";
    String NEW = "new";
    String EXPIRED = "expired";
    
    /**
     * The backend store's clock, in milliseconds.
     */
    @GetExchange("time")
    long serverTime();
    
    /**
     * Claim a machine id: {@code kind} is {@link #SELF}, {@link #RELEASED}, {@link #NEW} or {@link #EXPIRED}.
     */
    @PostExchange("{namespace}/claims/{kind}")
    LeaseResponse claim(@PathVariable String namespace, @PathVariable String kind, @RequestBody ClaimRequest request);
    
    @PostExchange("{namespace}/renewals")
    LeaseResponse renew(@PathVariable String namespace, @RequestBody RenewRequest request);
    
    /**
     * Release a lease.
     *
     * @return {@code false} if the lease was taken over or changed since
     */
    @PostExchange("{namespace}/releases")
    boolean release(@PathVariable String namespace, @RequestBody ReleaseRequest request);
}
