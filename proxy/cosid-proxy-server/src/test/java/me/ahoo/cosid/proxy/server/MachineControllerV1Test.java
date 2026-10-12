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

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.proxy.api.ErrorResponse;
import me.ahoo.cosid.proxy.server.controller.MachineController;
import me.ahoo.cosid.proxy.server.error.GlobalRestExceptionHandler;
import me.ahoo.cosid.test.machine.lease.InMemoryMachineLeaseStore;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

/**
 * The deprecated version 1 machine API on the lease store: stateless, so any server node, also after a restart, serves
 * any client, with or without the machine state 3.x clients send.
 */
class MachineControllerV1Test {
    private static final String NAMESPACE = "test_namespace";
    private final InMemoryMachineLeaseStore store = new InMemoryMachineLeaseStore();
    
    /**
     * A fresh server node on the shared store.
     */
    private WebTestClient newServerNode() {
        return WebTestClient
            .bindToController(new MachineController(store))
            .controllerAdvice(new GlobalRestExceptionHandler())
            .build();
    }
    
    private MachineState distribute(WebTestClient server, String instanceId) {
        return server.post()
            .uri("/machines/{ns}?machineBit=8&instanceId={id}&stable=false&safeGuardDuration=PT30S", NAMESPACE, instanceId)
            .exchange()
            .expectStatus().isOk()
            .expectBody(MachineStateBody.class)
            .returnResult()
            .getResponseBody()
            .toMachineState();
    }
    
    private boolean holds(String instanceId) {
        return store.claimSelf(NAMESPACE, InstanceId.of(instanceId, false), System.currentTimeMillis()).isPresent();
    }
    
    @Test
    void guardAndRevertWorkAcrossServerNodes() {
        MachineState state = distribute(newServerNode(), "node-1");
        
        newServerNode().patch()
            .uri("/machines/{ns}?instanceId=node-1&stable=false&safeGuardDuration=PT30S&machineId={m}&lastTimeStamp={t}",
                NAMESPACE, state.getMachineId(), state.getLastTimeStamp())
            .exchange()
            .expectStatus().isOk();
        newServerNode().delete()
            .uri("/machines/{ns}?instanceId=node-1&stable=false&machineId={m}&lastTimeStamp={t}",
                NAMESPACE, state.getMachineId(), state.getLastTimeStamp())
            .exchange()
            .expectStatus().isOk();
        
        assertThat(holds("node-1")).isFalse();
    }
    
    /**
     * A 3.x client that sends no machine state can guard and revert on any node, also after a restart.
     */
    @Test
    void clientWithoutMachineStateCanGuardAndRevertOnAnyNode() {
        distribute(newServerNode(), "node-1");
        
        newServerNode().patch()
            .uri("/machines/{ns}?instanceId=node-1&stable=false&safeGuardDuration=PT30S", NAMESPACE)
            .exchange()
            .expectStatus().isOk();
        newServerNode().delete()
            .uri("/machines/{ns}?instanceId=node-1&stable=false", NAMESPACE)
            .exchange()
            .expectStatus().isOk();
        
        assertThat(holds("node-1")).isFalse();
    }
    
    @Test
    void releasedMachineIdIsHandedOutAgain() {
        WebTestClient server = newServerNode();
        MachineState state = distribute(server, "node-1");
        server.delete()
            .uri("/machines/{ns}?instanceId=node-1&stable=false&machineId={m}&lastTimeStamp={t}",
                NAMESPACE, state.getMachineId(), state.getLastTimeStamp() + 5000)
            .exchange()
            .expectStatus().isOk();
        
        MachineState next = distribute(server, "node-2");
        
        assertThat(next.getMachineId()).isEqualTo(state.getMachineId());
        assertThat(next.getLastTimeStamp()).isGreaterThanOrEqualTo(state.getLastTimeStamp() + 5000);
    }
    
    @Test
    void guardReportsLostForAnotherMachineIdOrNoLease() {
        MachineState state = distribute(newServerNode(), "node-1");
        
        newServerNode().patch()
            .uri("/machines/{ns}?instanceId=node-1&stable=false&safeGuardDuration=PT30S&machineId={m}&lastTimeStamp={t}",
                NAMESPACE, state.getMachineId() + 1, state.getLastTimeStamp())
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.code").isEqualTo(ErrorResponse.MACHINE_ID_LOST);
        newServerNode().patch()
            .uri("/machines/{ns}?instanceId=stranger&stable=false&safeGuardDuration=PT30S", NAMESPACE)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.code").isEqualTo(ErrorResponse.MACHINE_ID_LOST);
    }
    
    @Test
    void lastTimeStampTooFarAheadIsBadRequest() {
        WebTestClient server = newServerNode();
        MachineState state = distribute(server, "node-1");
        long tooFar = System.currentTimeMillis() + Duration.ofMinutes(10).toMillis();
        
        server.patch()
            .uri("/machines/{ns}?instanceId=node-1&stable=false&safeGuardDuration=PT30S&machineId={m}&lastTimeStamp={t}",
                NAMESPACE, state.getMachineId(), tooFar)
            .exchange()
            .expectStatus().isBadRequest();
        server.delete()
            .uri("/machines/{ns}?instanceId=node-1&stable=false&machineId={m}&lastTimeStamp={t}", NAMESPACE, state.getMachineId(), tooFar)
            .exchange()
            .expectStatus().isBadRequest();
        
        assertThat(holds("node-1")).isTrue();
    }
    
    @Test
    void invalidArgumentsAreBadRequests() {
        WebTestClient server = newServerNode();
        server.post()
            .uri("/machines/{ns}?machineBit=0&instanceId=node-1&stable=false&safeGuardDuration=PT30S", NAMESPACE)
            .exchange()
            .expectStatus().isBadRequest();
        server.post()
            .uri("/machines/{ns}?machineBit=8&instanceId=node-1&stable=false&safeGuardDuration=30s", NAMESPACE)
            .exchange()
            .expectStatus().isBadRequest();
        server.post()
            .uri("/machines/{ns}?machineBit=8&instanceId= &stable=false&safeGuardDuration=PT30S", NAMESPACE)
            .exchange()
            .expectStatus().isBadRequest();
        server.patch()
            .uri("/machines/{ns}?instanceId=node-1&stable=false&safeGuardDuration=PT30S&machineId=1", NAMESPACE)
            .exchange()
            .expectStatus().isBadRequest();
        server.patch()
            .uri("/machines/{ns}?instanceId=node-1&stable=false&safeGuardDuration=PT30S&machineId=-1&lastTimeStamp=1", NAMESPACE)
            .exchange()
            .expectStatus().isBadRequest();
        assertThat(holds("node-1")).isFalse();
    }
    
    record MachineStateBody(int machineId, long lastTimeStamp) {
        MachineState toMachineState() {
            return MachineState.of(machineId, lastTimeStamp);
        }
    }
}
