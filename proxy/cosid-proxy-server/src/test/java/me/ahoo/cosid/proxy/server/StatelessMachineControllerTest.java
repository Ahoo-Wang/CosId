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

import me.ahoo.cosid.machine.AbstractMachineIdDistributor;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InMemoryMachineStateStorage;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.proxy.api.ErrorResponse;
import me.ahoo.cosid.proxy.server.controller.MachineController;
import me.ahoo.cosid.proxy.server.error.GlobalRestExceptionHandler;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The proxy server must not keep client machine state: a restarted server, or another server node behind the
 * same load balancer, has to serve guard and revert for machine ids it never distributed itself.
 */
class StatelessMachineControllerTest {
    private static final String NAMESPACE = "test_namespace";
    private final SharedStore store = new SharedStore();
    private final AtomicInteger clockSyncCalls = new AtomicInteger();

    /**
     * A fresh server node: new distributor with its own empty local storage, same remote store.
     */
    private WebTestClient newServerNode() {
        ClockBackwardsSynchronizer recordingSynchronizer = new ClockBackwardsSynchronizer() {
            @Override
            public void sync(long lastTimestamp) {
                clockSyncCalls.incrementAndGet();
            }

            @Override
            public void syncUninterruptibly(long lastTimestamp) {
                clockSyncCalls.incrementAndGet();
            }
        };
        StoreBackedMachineIdDistributor distributor = new StoreBackedMachineIdDistributor(store, recordingSynchronizer);
        return WebTestClient
            .bindToController(new MachineController(distributor))
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

    @Test
    void guardSucceedsAfterServerRestart() {
        MachineState state = distribute(newServerNode(), "node-1");

        WebTestClient restarted = newServerNode();
        restarted.patch()
            .uri("/machines/{ns}?instanceId=node-1&stable=false&safeGuardDuration=PT30S&machineId={m}&lastTimeStamp={t}",
                NAMESPACE, state.getMachineId(), state.getLastTimeStamp() + 1000)
            .exchange()
            .expectStatus().isOk();

        assertThat(store.states.get("node-1").getLastTimeStamp()).isEqualTo(state.getLastTimeStamp() + 1000);
    }

    @Test
    void guardAndRevertWorkAcrossServerNodes() {
        WebTestClient nodeA = newServerNode();
        WebTestClient nodeB = newServerNode();
        MachineState state = distribute(nodeA, "node-1");

        nodeB.patch()
            .uri("/machines/{ns}?instanceId=node-1&stable=false&safeGuardDuration=PT30S&machineId={m}&lastTimeStamp={t}",
                NAMESPACE, state.getMachineId(), state.getLastTimeStamp())
            .exchange()
            .expectStatus().isOk();

        nodeB.delete()
            .uri("/machines/{ns}?instanceId=node-1&stable=false&machineId={m}&lastTimeStamp={t}",
                NAMESPACE, state.getMachineId(), state.getLastTimeStamp())
            .exchange()
            .expectStatus().isOk();

        assertThat(store.states).doesNotContainKey("node-1");
    }

    @Test
    void legacyGuardWithoutClientStateStillFailsAfterRestart() {
        distribute(newServerNode(), "node-1");

        newServerNode().patch()
            .uri("/machines/{ns}?instanceId=node-1&stable=false&safeGuardDuration=PT30S", NAMESPACE)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.code").isEqualTo(ErrorResponse.NOT_FOUND_MACHINE_STATE);
    }

    @Test
    void guardReportsLostWhenMachineIdWasTakenOver() {
        MachineState state = distribute(newServerNode(), "node-1");

        newServerNode().patch()
            .uri("/machines/{ns}?instanceId=node-1&stable=false&safeGuardDuration=PT30S&machineId={m}&lastTimeStamp={t}",
                NAMESPACE, state.getMachineId() + 1, state.getLastTimeStamp())
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.code").isEqualTo(ErrorResponse.MACHINE_ID_LOST);
    }

    @Test
    void serverNeverSynchronizesItsOwnClockAgainstClientState() {
        long future = System.currentTimeMillis() + Duration.ofHours(1).toMillis();
        store.states.put("node-1", MachineState.of(0, future));
        WebTestClient server = newServerNode();

        MachineState state = distribute(server, "node-1");
        server.patch()
            .uri("/machines/{ns}?instanceId=node-1&stable=false&safeGuardDuration=PT30S&machineId=0&lastTimeStamp={t}", NAMESPACE, future)
            .exchange()
            .expectStatus().isOk();

        assertThat(state.getLastTimeStamp()).isEqualTo(future);
        assertThat(clockSyncCalls).hasValue(0);
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
        assertThat(store.states).isEmpty();
    }

    record MachineStateBody(int machineId, long lastTimeStamp) {
        MachineState toMachineState() {
            return MachineState.of(machineId, lastTimeStamp);
        }
    }

    /**
     * Stands in for Redis/JDBC/ZooKeeper: the state every server node shares.
     */
    static class SharedStore {
        private final Map<String, MachineState> states = new ConcurrentHashMap<>();
        private final AtomicInteger nextMachineId = new AtomicInteger();
    }

    static class StoreBackedMachineIdDistributor extends AbstractMachineIdDistributor {
        private final SharedStore store;

        StoreBackedMachineIdDistributor(SharedStore store, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
            super(new InMemoryMachineStateStorage(), clockBackwardsSynchronizer);
            this.store = store;
        }

        @Override
        protected MachineState distributeRemote(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
            return store.states.computeIfAbsent(instanceId.getInstanceId(),
                key -> MachineState.of(store.nextMachineId.getAndIncrement(), System.currentTimeMillis()));
        }

        @Override
        protected void revertRemote(String namespace, InstanceId instanceId, MachineState machineState) {
            store.states.remove(instanceId.getInstanceId(), store.states.get(instanceId.getInstanceId()));
        }

        @Override
        protected void guardRemote(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) {
            MachineState current = store.states.get(instanceId.getInstanceId());
            if (current == null || current.getMachineId() != machineState.getMachineId()) {
                throw new MachineIdLostException(namespace, instanceId, machineState);
            }
            store.states.put(instanceId.getInstanceId(), machineState);
        }
    }
}
