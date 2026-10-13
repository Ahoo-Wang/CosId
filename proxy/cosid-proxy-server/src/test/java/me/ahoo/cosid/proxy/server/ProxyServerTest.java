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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.proxy.api.ErrorResponse;
import me.ahoo.cosid.proxy.server.configuration.ProxyServerProperties;
import me.ahoo.cosid.proxy.server.controller.MachineController;
import me.ahoo.cosid.proxy.server.controller.SegmentController;
import me.ahoo.cosid.proxy.server.error.GlobalRestExceptionHandler;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.segment.grouped.GroupedKey;
import me.ahoo.cosid.test.machine.lease.InMemoryMachineLeaseStore;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ProxyServerTest .
 *
 * @author ahoo wang
 */
class ProxyServerTest {
    private final RecordingIdSegmentDistributorFactory segmentDistributorFactory = new RecordingIdSegmentDistributorFactory();
    private final InMemoryMachineLeaseStore leaseStore = new InMemoryMachineLeaseStore();
    private final WebTestClient webTestClient = WebTestClient
        .bindToController(new SegmentController(segmentDistributorFactory), new MachineController(leaseStore, shortSafeGuards()))
        .controllerAdvice(new GlobalRestExceptionHandler())
            .build();

    private static ProxyServerProperties shortSafeGuards() {
        ProxyServerProperties properties = new ProxyServerProperties();
        properties.setMinSafeGuardDuration(Duration.ZERO);
        return properties;
    }

    @Test
    void segmentStepIsCappedByDefaultAndOffsetOnlyAgainstOverflow() {
        webTestClient.patch().uri("/segments/test_namespace/large?step=1000000000000").exchange().expectStatus().isOk();
        webTestClient.patch().uri("/segments/test_namespace/large?step=1000000000001").exchange().expectStatus().isBadRequest();
        webTestClient.patch().uri("/segments/test_namespace/huge?step=9223372036854775000").exchange().expectStatus().isBadRequest();
        webTestClient.post().uri("/segments/distributor/test_namespace/existing?offset=10000000000&step=100").exchange().expectStatus().isOk();
        webTestClient.post().uri("/segments/distributor/test_namespace/far?offset=4611686018427387904&step=1").exchange().expectStatus().isBadRequest();
        webTestClient.patch().uri("/segments/test_namespace/large?step=5").exchange().expectStatus().isOk();
    }
    
    @Test
    void zeroMaxStepIsUnlimited() {
        ProxyServerProperties properties = new ProxyServerProperties();
        properties.getSegment().setMaxStep(0);
        WebTestClient unlimited = WebTestClient
            .bindToController(new SegmentController(segmentDistributorFactory, properties))
            .controllerAdvice(new GlobalRestExceptionHandler())
            .build();
        unlimited.patch().uri("/segments/test_namespace/unlimited?step=2000000000000").exchange().expectStatus().isOk();
    }
    
    @Test
    void guardDurationsMustNotBeNegative() {
        ProxyServerProperties properties = new ProxyServerProperties();
        assertThatThrownBy(() -> properties.setMinSafeGuardDuration(Duration.ofSeconds(-1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setMinSafeGuardDuration(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setMaxClockSkew(Duration.ofSeconds(-1))).isInstanceOf(IllegalArgumentException.class);
        properties.setMaxClockSkew(Duration.ZERO);
        assertThat(properties.getMaxClockSkew()).isZero();
    }
    
    @Test
    void segmentStepCanBeCapped() {

        me.ahoo.cosid.proxy.server.configuration.ProxyServerProperties properties = new me.ahoo.cosid.proxy.server.configuration.ProxyServerProperties();
        properties.getSegment().setMaxStep(1000);
        WebTestClient capped = WebTestClient
            .bindToController(new SegmentController(segmentDistributorFactory, properties))
            .controllerAdvice(new GlobalRestExceptionHandler())
            .build();
        capped.patch().uri("/segments/test_namespace/capped?step=1000").exchange().expectStatus().isOk();
        capped.patch().uri("/segments/test_namespace/capped?step=1001").exchange().expectStatus().isBadRequest();
        capped.post().uri("/segments/distributor/test_namespace/capped2?offset=0&step=1001").exchange().expectStatus().isBadRequest();
    }

    @Test
    void segmentDistributorsAreCapped() {
        ProxyServerProperties properties = new ProxyServerProperties();
        assertThat(properties.getSegment().getMaxDistributors()).isZero();
        properties.getSegment().setMaxDistributors(2);
        WebTestClient capped = WebTestClient
            .bindToController(new SegmentController(segmentDistributorFactory, properties))
            .controllerAdvice(new GlobalRestExceptionHandler())
            .build();
        capped.post().uri("/segments/distributor/test_namespace/first?offset=0&step=10").exchange().expectStatus().isOk();
        capped.patch().uri("/segments/test_namespace/second?step=10").exchange().expectStatus().isOk();
        
        capped.patch().uri("/segments/test_namespace/third?step=10").exchange()
            .expectStatus().isEqualTo(429)
            .expectBody(ErrorResponse.class)
            .value(error -> assertThat(error.getCode()).isEqualTo(ErrorResponse.TOO_MANY_DISTRIBUTORS));
        capped.post().uri("/segments/distributor/other_namespace/first?offset=0&step=10").exchange().expectStatus().isEqualTo(429);
        
        // The distributors it already keeps go on working.
        capped.patch().uri("/segments/test_namespace/first?step=10").exchange().expectStatus().isOk();
        capped.post().uri("/segments/distributor/test_namespace/second?offset=0&step=10").exchange().expectStatus().isOk();
    }
    
    @Test
    void segmentArgumentsAreValidated() {
        webTestClient.post().uri("/segments/distributor/test_namespace/order?offset=-1&step=20").exchange().expectStatus().isBadRequest();
        webTestClient.post().uri("/segments/distributor/test_namespace/order?offset=0&step=0").exchange().expectStatus().isBadRequest();
        webTestClient.post().uri("/segments/distributor/test_namespace/order?offset=9223372036854775807&step=1").exchange().expectStatus().isBadRequest();
        webTestClient.patch().uri("/segments/test_namespace/order?step=-5").exchange().expectStatus().isBadRequest();
        webTestClient.patch().uri(java.net.URI.create("/segments/%20/order?step=5")).exchange().expectStatus().isBadRequest();

        assertThat(segmentDistributorFactory.lastDefinition.get()).isNull();
    }

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
    void nextMaxIdShouldSelfHealAfterServerRestart() {
        StatefulIdSegmentDistributorFactory backendFactory = new StatefulIdSegmentDistributorFactory();
        WebTestClient serverBeforeRestart = WebTestClient
            .bindToController(new SegmentController(backendFactory))
            .controllerAdvice(new GlobalRestExceptionHandler())
            .build();

        serverBeforeRestart
            .post()
            .uri("/segments/distributor/test_namespace/order?offset=100&step=20")
            .exchange()
            .expectStatus().isOk()
            .expectBody().isEmpty();

        serverBeforeRestart
            .patch()
            .uri("/segments/test_namespace/order?step=20")
            .exchange()
            .expectStatus().isOk()
            .expectBody(Long.class)
            .isEqualTo(120L);

        WebTestClient serverAfterRestart = WebTestClient
            .bindToController(new SegmentController(backendFactory))
            .controllerAdvice(new GlobalRestExceptionHandler())
            .build();

        serverAfterRestart
            .patch()
            .uri("/segments/test_namespace/order?step=5")
            .exchange()
            .expectStatus().isOk()
            .expectBody(Long.class)
            .isEqualTo(125L);
    }

    /**
     * Contract lock for the deliberate lazy-initialization semantics of nextMaxId:
     * a never-created name starts from DEFAULT_OFFSET instead of failing. Not a bug
     * reproduction — this behavior is GREEN by design since the self-heal fix.
     */
    @Test
    void nextMaxIdShouldLazilyInitializeNeverCreatedSegment() {
        StatefulIdSegmentDistributorFactory backendFactory = new StatefulIdSegmentDistributorFactory();
        WebTestClient server = WebTestClient
            .bindToController(new SegmentController(backendFactory))
            .controllerAdvice(new GlobalRestExceptionHandler())
            .build();

        server
            .patch()
            .uri("/segments/test_namespace/fresh?step=10")
            .exchange()
            .expectStatus().isOk()
            .expectBody(Long.class)
            .isEqualTo(10L);
    }

    @Test
    void machineDistributeEndpointMapsQueryParametersAndResponseBody() {
        webTestClient
            .post()
            .uri("/machines/test_namespace?machineBit=8&instanceId=node-1&stable=true&safeGuardDuration=PT30S")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.machineId").isEqualTo(0)
            .jsonPath("$.lastTimeStamp").value(lastTimeStamp -> assertThat(((Number) lastTimeStamp).longValue()).isPositive());

        assertThat(leaseStore.claimSelf("test_namespace", new InstanceId("node-1", true), System.currentTimeMillis()))
            .hasValueSatisfying(lease -> assertThat(lease.isStable()).isTrue());
    }

    @Test
    void machineOverflowMapsToBadRequestErrorBody() {
        leaseStore.claimNew("overflow", new InstanceId("held-0", true), 1, System.currentTimeMillis());
        leaseStore.claimNew("overflow", new InstanceId("held-1", true), 1, System.currentTimeMillis());
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

    private static class StatefulIdSegmentDistributorFactory implements IdSegmentDistributorFactory {
        private final ConcurrentHashMap<String, AtomicLong> maxIds = new ConcurrentHashMap<>();

        @Override
        public IdSegmentDistributor create(IdSegmentDistributorDefinition definition) {
            AtomicLong maxId = maxIds.computeIfAbsent(definition.getNamespacedName(),
                key -> new AtomicLong(definition.getOffset()));
            return new StatefulIdSegmentDistributor(definition, maxId);
        }
    }

    private static class StatefulIdSegmentDistributor implements IdSegmentDistributor {
        private final IdSegmentDistributorDefinition definition;
        private final AtomicLong maxId;

        StatefulIdSegmentDistributor(IdSegmentDistributorDefinition definition, AtomicLong maxId) {
            this.definition = definition;
            this.maxId = maxId;
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
            return maxId.addAndGet(step);
        }
    }
}
