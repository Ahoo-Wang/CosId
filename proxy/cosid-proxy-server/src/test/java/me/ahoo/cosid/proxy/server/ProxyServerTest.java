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
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineIdOverflowException;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.proxy.api.ErrorResponse;
import me.ahoo.cosid.proxy.server.controller.MachineController;
import me.ahoo.cosid.proxy.server.controller.SegmentController;
import me.ahoo.cosid.proxy.server.error.GlobalRestExceptionHandler;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.segment.grouped.GroupedKey;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ProxyServerTest .
 *
 * @author ahoo wang
 */
class ProxyServerTest {
    private final RecordingIdSegmentDistributorFactory segmentDistributorFactory = new RecordingIdSegmentDistributorFactory();
    private final RecordingMachineIdDistributor machineIdDistributor = new RecordingMachineIdDistributor();
    private final WebTestClient webTestClient = WebTestClient
        .bindToController(new SegmentController(segmentDistributorFactory), new MachineController(machineIdDistributor))
        .controllerAdvice(new GlobalRestExceptionHandler())
            .build();

    @Test
    void segmentEndpointsExposeCreateAndNextMaxIdContract() {
        webTestClient
            .post()
            .uri("/segments/distributor/test_namespace/order?offset=100&step=20")
            .exchange()
            .expectStatus().isOk()
            .expectBody().isEmpty();

        assertThat(segmentDistributorFactory.lastDefinition.get().getNamespace()).isEqualTo("test_namespace");
        assertThat(segmentDistributorFactory.lastDefinition.get().getName()).isEqualTo("order");
        assertThat(segmentDistributorFactory.lastDefinition.get().getOffset()).isEqualTo(100);
        assertThat(segmentDistributorFactory.lastDefinition.get().getStep()).isEqualTo(20);

        webTestClient
            .patch()
            .uri("/segments/test_namespace/order?step=5")
            .exchange()
            .expectStatus().isOk()
            .expectBody(Long.class)
            .isEqualTo(105L);
    }

    @Test
    void machineDistributeEndpointMapsQueryParametersAndResponseBody() {
        webTestClient
            .post()
            .uri("/machines/test_namespace?machineBit=8&instanceId=node-1&stable=true&safeGuardDuration=PT30S")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.machineId").isEqualTo(6)
            .jsonPath("$.lastTimeStamp").isEqualTo(123456);

        assertThat(machineIdDistributor.lastNamespace).isEqualTo("test_namespace");
        assertThat(machineIdDistributor.lastMachineBit).isEqualTo(8);
        assertThat(machineIdDistributor.lastInstanceId).isEqualTo(new InstanceId("node-1", true));
        assertThat(machineIdDistributor.lastSafeGuardDuration).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void machineOverflowMapsToBadRequestErrorBody() {
        webTestClient
            .post()
            .uri("/machines/overflow?machineBit=1&instanceId=node-1&stable=false&safeGuardDuration=PT30S")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.code").isEqualTo(ErrorResponse.MACHINE_ID_OVERFLOW);
    }

    @Test
    void machineGuardMapsLostErrorToBadRequestErrorBody() {
        webTestClient
            .patch()
            .uri("/machines/lost?instanceId=node-1&stable=false&safeGuardDuration=PT30S")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.code").isEqualTo(ErrorResponse.MACHINE_ID_LOST);
    }

    private static class RecordingIdSegmentDistributorFactory implements IdSegmentDistributorFactory {
        private final AtomicReference<IdSegmentDistributorDefinition> lastDefinition = new AtomicReference<>();

        @Override
        public IdSegmentDistributor create(IdSegmentDistributorDefinition definition) {
            lastDefinition.set(definition);
            return new RecordingIdSegmentDistributor(definition);
        }
    }

    private static class RecordingIdSegmentDistributor implements IdSegmentDistributor {
        private final IdSegmentDistributorDefinition definition;
        private long maxId;

        RecordingIdSegmentDistributor(IdSegmentDistributorDefinition definition) {
            this.definition = definition;
            this.maxId = definition.getOffset();
        }

        @Override
        public String getNamespace() {
            return definition.getNamespace();
        }

        @Override
        public String getName() {
            return definition.getName();
        }

        @Override
        public long getStep() {
            return definition.getStep();
        }

        @Override
        public GroupedKey group() {
            return GroupedKey.NEVER;
        }

        @Override
        public long nextMaxId(long step) {
            maxId += step;
            return maxId;
        }
    }

    private static class RecordingMachineIdDistributor implements MachineIdDistributor {
        private String lastNamespace;
        private int lastMachineBit;
        private InstanceId lastInstanceId;
        private Duration lastSafeGuardDuration;

        @Override
        public MachineState distribute(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
            this.lastNamespace = namespace;
            this.lastMachineBit = machineBit;
            this.lastInstanceId = instanceId;
            this.lastSafeGuardDuration = safeGuardDuration;
            if ("overflow".equals(namespace)) {
                throw new MachineIdOverflowException(machineBit, instanceId);
            }
            return MachineState.of(6, 123456);
        }

        @Override
        public void revert(String namespace, InstanceId instanceId) {
        }

        @Override
        public void guard(String namespace, InstanceId instanceId, Duration safeGuardDuration) {
            if ("lost".equals(namespace)) {
                throw new MachineIdLostException(namespace, instanceId, MachineState.of(6, 123456));
            }
        }
    }
}
