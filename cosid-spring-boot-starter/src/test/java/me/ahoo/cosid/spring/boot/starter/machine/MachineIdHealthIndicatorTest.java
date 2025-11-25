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

package me.ahoo.cosid.spring.boot.starter.machine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import me.ahoo.cosid.machine.GuardianState;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdGuarder;
import me.ahoo.cosid.machine.NamespacedInstanceId;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests for {@link MachineIdHealthIndicator}.
 */
class MachineIdHealthIndicatorTest {

    /**
     * Test implementation of MachineIdGuarder for testing purposes.
     * Allows setting guardian states directly.
     */
    static class TestMachineIdGuarder implements MachineIdGuarder {
        private final Map<NamespacedInstanceId, GuardianState> guardianStates = new HashMap<>();

        public void setGuardianState(NamespacedInstanceId id, GuardianState state) {
            guardianStates.put(id, state);
        }

        @Override
        public Map<NamespacedInstanceId, GuardianState> getGuardianStates() {
            return Map.copyOf(guardianStates);
        }

        @Override
        public void register(String namespace, InstanceId instanceId) {
            // Not implemented for test
        }

        @Override
        public void unregister(String namespace, InstanceId instanceId) {
            // Not implemented for test
        }

        @Override
        public void start() {
            // Not implemented for test
        }

        @Override
        public void stop() {
            // Not implemented for test
        }

        @Override
        public boolean isRunning() {
            return true;
        }
    }

    @Test
    void testConstructorWithValidGuarder() {
        MachineIdGuarder guarder = MachineIdGuarder.NONE;
        MachineIdHealthIndicator indicator = new MachineIdHealthIndicator(guarder);

        assertThat(indicator, notNullValue());
    }

    @Test
    void testConstructorWithNullGuarder() {
        // Note: Constructor does not validate null, despite documentation
        MachineIdHealthIndicator indicator = new MachineIdHealthIndicator(null);

        assertThat(indicator, notNullValue());
    }

    @Test
    void testHealthWhenNoGuardianStates() {
        MachineIdGuarder guarder = MachineIdGuarder.NONE;
        MachineIdHealthIndicator indicator = new MachineIdHealthIndicator(guarder);

        Health health = indicator.health();

        assertThat(health.getStatus(), equalTo(Status.UP));
        assertThat(health.getDetails().isEmpty(), equalTo(true));
    }

    @Test
    void testHealthWhenAllStatesAreSuccessful() {
        TestMachineIdGuarder guarder = new TestMachineIdGuarder();
        NamespacedInstanceId id1 = new NamespacedInstanceId("ns1", InstanceId.of("inst1", false));
        NamespacedInstanceId id2 = new NamespacedInstanceId("ns2", InstanceId.of("inst2", false));

        guarder.setGuardianState(id1, new GuardianState(System.currentTimeMillis(), null));
        guarder.setGuardianState(id2, new GuardianState(System.currentTimeMillis() + 1000, null));

        MachineIdHealthIndicator indicator = new MachineIdHealthIndicator(guarder);

        Health health = indicator.health();

        assertThat(health.getStatus(), equalTo(Status.UP));
        assertThat(health.getDetails().isEmpty(), equalTo(true));
    }

    @Test
    void testHealthWhenOneStateIsFailed() {
        TestMachineIdGuarder guarder = new TestMachineIdGuarder();
        NamespacedInstanceId id1 = new NamespacedInstanceId("ns1", InstanceId.of("inst1", false));
        NamespacedInstanceId id2 = new NamespacedInstanceId("ns2", InstanceId.of("inst2", false));

        long guardAt = System.currentTimeMillis();
        RuntimeException error = new RuntimeException("Test failure");

        guarder.setGuardianState(id1, new GuardianState(guardAt, null));
        guarder.setGuardianState(id2, new GuardianState(guardAt + 1000, error));

        MachineIdHealthIndicator indicator = new MachineIdHealthIndicator(guarder);

        Health health = indicator.health();

        assertThat(health.getStatus(), equalTo(Status.DOWN));
        assertThat(health.getDetails().get("guardAt"), equalTo(guardAt + 1000));
    }

    @Test
    void testHealthWithDifferentErrorTypes() {
        TestMachineIdGuarder guarder = new TestMachineIdGuarder();
        NamespacedInstanceId id = new NamespacedInstanceId("ns", InstanceId.of("inst", false));

        long guardAt = System.currentTimeMillis();

        // Test with Exception
        Exception exception = new Exception("Exception failure");
        guarder.setGuardianState(id, new GuardianState(guardAt, exception));

        MachineIdHealthIndicator indicator = new MachineIdHealthIndicator(guarder);
        Health health = indicator.health();

        assertThat(health.getStatus(), equalTo(Status.DOWN));

        // Test with Error
        Error error = new Error("Error failure");
        guarder.setGuardianState(id, new GuardianState(guardAt + 1000, error));

        health = indicator.health();
        assertThat(health.getStatus(), equalTo(Status.DOWN));
    }

    @Test
    void testHealthWithNullErrorInFailedState() {
        // This shouldn't happen in practice, but test edge case
        TestMachineIdGuarder guarder = new TestMachineIdGuarder();
        NamespacedInstanceId id = new NamespacedInstanceId("ns", InstanceId.of("inst", false));

        // Manually create a state with null error but somehow marked as failed
        // Since isFailed() checks error != null, this won't be considered failed
        GuardianState state = new GuardianState(System.currentTimeMillis(), null);
        guarder.setGuardianState(id, state);

        MachineIdHealthIndicator indicator = new MachineIdHealthIndicator(guarder);

        Health health = indicator.health();

        assertThat(health.getStatus(), equalTo(Status.UP));
    }

    @Test
    void testHealthWithEmptyGuarderAfterSettingStates() {
        TestMachineIdGuarder guarder = new TestMachineIdGuarder();
        // No states set

        MachineIdHealthIndicator indicator = new MachineIdHealthIndicator(guarder);

        Health health = indicator.health();

        assertThat(health.getStatus(), equalTo(Status.UP));
    }

    @Test
    void testHealthIndicatorImplementsInterface() {
        MachineIdGuarder guarder = MachineIdGuarder.NONE;
        MachineIdHealthIndicator indicator = new MachineIdHealthIndicator(guarder);

        assertThat(indicator, instanceOf(org.springframework.boot.actuate.health.HealthIndicator.class));
    }

    @Test
    void testHealthWithLargeGuardAtValue() {
        TestMachineIdGuarder guarder = new TestMachineIdGuarder();
        NamespacedInstanceId id = new NamespacedInstanceId("ns", InstanceId.of("inst", false));

        long guardAt = Long.MAX_VALUE;
        RuntimeException error = new RuntimeException("Max value failure");

        guarder.setGuardianState(id, new GuardianState(guardAt, error));

        MachineIdHealthIndicator indicator = new MachineIdHealthIndicator(guarder);

        Health health = indicator.health();

        assertThat(health.getStatus(), equalTo(Status.DOWN));
        assertThat(health.getDetails().get("guardAt"), equalTo(guardAt));
    }

    @Test
    void testHealthWithNegativeGuardAtValue() {
        TestMachineIdGuarder guarder = new TestMachineIdGuarder();
        NamespacedInstanceId id = new NamespacedInstanceId("ns", InstanceId.of("inst", false));

        long guardAt = -1L;
        RuntimeException error = new RuntimeException("Negative time failure");

        guarder.setGuardianState(id, new GuardianState(guardAt, error));

        MachineIdHealthIndicator indicator = new MachineIdHealthIndicator(guarder);

        Health health = indicator.health();

        assertThat(health.getStatus(), equalTo(Status.DOWN));
        assertThat(health.getDetails().get("guardAt"), equalTo(guardAt));
    }
}