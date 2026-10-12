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

package me.ahoo.cosid.proxy.server;

import static org.assertj.core.api.Assertions.assertThat;

import me.ahoo.cosid.proxy.api.ClaimRequest;
import me.ahoo.cosid.proxy.api.LeaseDto;
import me.ahoo.cosid.proxy.api.LeaseResponse;
import me.ahoo.cosid.proxy.api.ReleaseRequest;
import me.ahoo.cosid.proxy.api.RenewRequest;
import me.ahoo.cosid.proxy.server.controller.MachineLeaseController;
import me.ahoo.cosid.proxy.server.error.GlobalRestExceptionHandler;
import me.ahoo.cosid.test.machine.lease.InMemoryMachineLeaseStore;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

class MachineLeaseControllerTest {
    private final WebTestClient server = WebTestClient
        .bindToController(new MachineLeaseController(new InMemoryMachineLeaseStore()))
        .controllerAdvice(new GlobalRestExceptionHandler())
        .build();
    
    private WebTestClient.ResponseSpec claim(String namespace, String kind, ClaimRequest request) {
        return server.post().uri("/machines/v2/{ns}/claims/{kind}", namespace, kind).bodyValue(request).exchange();
    }
    
    @Test
    void aRequestWithoutALeaseIsABadRequest() {
        long now = System.currentTimeMillis();
        server.post().uri("/machines/v2/{ns}/renewals", "ns").bodyValue(new RenewRequest(null, now)).exchange()
            .expectStatus().isBadRequest()
            .expectBody().jsonPath("$.msg").isEqualTo("lease can not be null!");
        server.post().uri("/machines/v2/{ns}/releases", "ns").bodyValue(new ReleaseRequest(null, now)).exchange()
            .expectStatus().isBadRequest()
            .expectBody().jsonPath("$.msg").isEqualTo("lease can not be null!");
    }
    
    @Test
    void claimNewThenSelfThenRenew() {
        long now = System.currentTimeMillis();
        LeaseDto lease = claim("ns", "new", new ClaimRequest("node-1", false, now, 3, 0))
            .expectStatus().isOk().expectBody(LeaseResponse.class).returnResult().getResponseBody().lease();
        assertThat(lease).isNotNull();
        assertThat(lease.machineId()).isZero();
        
        LeaseDto self = claim("ns", "self", new ClaimRequest("node-1", false, now + 1, 0, 0))
            .expectStatus().isOk().expectBody(LeaseResponse.class).returnResult().getResponseBody().lease();
        assertThat(self).isNotNull();
        LeaseResponse renewed = server.post().uri("/machines/v2/{ns}/renewals", "ns").bodyValue(new RenewRequest(self, now + 2))
            .exchange().expectStatus().isOk().expectBody(LeaseResponse.class).returnResult().getResponseBody();
        assertThat(renewed.lease()).isNotNull();
        LeaseResponse stale = server.post().uri("/machines/v2/{ns}/renewals", "ns").bodyValue(new RenewRequest(self, now + 3))
            .exchange().expectStatus().isOk().expectBody(LeaseResponse.class).returnResult().getResponseBody();
        assertThat(stale.lease()).isNull();
    }
    
    @Test
    void claimOfNothingIsAnEmptyLease() {
        LeaseResponse response = claim("ns", "self", new ClaimRequest("stranger", false, System.currentTimeMillis(), 0, 0))
            .expectStatus().isOk().expectBody(LeaseResponse.class).returnResult().getResponseBody();
        assertThat(response.lease()).isNull();
    }
    
    @Test
    void invalidRequestsAreBadRequests() {
        long now = System.currentTimeMillis();
        claim("ns", "unknown", new ClaimRequest("node-1", false, now, 3, 0)).expectStatus().isBadRequest();
        claim("ns", "new", new ClaimRequest(" ", false, now, 3, 0)).expectStatus().isBadRequest();
        claim("ns", "new", new ClaimRequest("node-1", false, now, -1, 0)).expectStatus().isBadRequest();
        claim("ns", "new", new ClaimRequest("node-1", false, now + Duration.ofMinutes(10).toMillis(), 3, 0)).expectStatus().isBadRequest();
        LeaseDto otherNamespace = new LeaseDto("other", "node-1", false, 0, 0, now, now);
        server.post().uri("/machines/v2/{ns}/renewals", "ns").bodyValue(new RenewRequest(otherNamespace, now))
            .exchange().expectStatus().isBadRequest();
    }

    @Test
    void expiredClaimWithACutOffTooRecentOrInTheFutureIsBadRequestAndTheHolderKeepsItsLease() {
        long now = System.currentTimeMillis();
        LeaseDto holder = claim("steal", "new", new ClaimRequest("holder", false, now, 0, 0))
            .expectStatus().isOk().expectBody(LeaseResponse.class).returnResult().getResponseBody().lease();
        
        claim("steal", "expired", new ClaimRequest("thief", false, now, 0, Long.MAX_VALUE)).expectStatus().isBadRequest();
        claim("steal", "expired", new ClaimRequest("thief", false, now, 0, now - Duration.ofMinutes(1).toMillis())).expectStatus().isBadRequest();
        LeaseResponse past = claim("steal", "expired", new ClaimRequest("thief", false, now, 0, now - Duration.ofMinutes(10).toMillis()))
            .expectStatus().isOk().expectBody(LeaseResponse.class).returnResult().getResponseBody();
        
        assertThat(past.lease()).isNull();
        LeaseResponse renewed = server.post().uri("/machines/v2/{ns}/renewals", "steal").bodyValue(new RenewRequest(holder, now + 1))
            .exchange().expectStatus().isOk().expectBody(LeaseResponse.class).returnResult().getResponseBody();
        assertThat(renewed.lease()).isNotNull();
    }
}
