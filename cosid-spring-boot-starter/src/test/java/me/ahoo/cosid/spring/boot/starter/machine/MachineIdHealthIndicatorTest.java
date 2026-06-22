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

import static org.assertj.core.api.Assertions.assertThat;

import me.ahoo.cosid.machine.GuardianState;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdGuarder;
import me.ahoo.cosid.machine.NamespacedInstanceId;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;

import java.util.LinkedHashMap;
import java.util.Map;

class MachineIdHealthIndicatorTest {

    @Test
    void reportsUpWhenGuarderHasNoFailures() {
        RecordingMachineIdGuarder guarder = new RecordingMachineIdGuarder()
            .put("orders", GuardianState.success(100))
            .put("billing", GuardianState.INITIAL);

        MachineIdHealthIndicator indicator = new MachineIdHealthIndicator(guarder);

        assertThat(indicator).isInstanceOf(HealthIndicator.class);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicator.health().getDetails()).isEmpty();
    }

    @Test
    void reportsDownWithFirstFailureDetails() {
        RuntimeException firstError = new RuntimeException("machine id lost");
        RecordingMachineIdGuarder guarder = new RecordingMachineIdGuarder()
            .put("orders", GuardianState.success(100))
            .put("billing", GuardianState.failed(200, firstError))
            .put("inventory", GuardianState.failed(300, new IllegalStateException("later failure")));

        var health = new MachineIdHealthIndicator(guarder).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("guardAt", 200L);
        assertThat(health.getDetails()).containsEntry("error", firstError.toString());
    }

    private static final class RecordingMachineIdGuarder implements MachineIdGuarder {
        private final Map<NamespacedInstanceId, GuardianState> guardianStates = new LinkedHashMap<>();

        RecordingMachineIdGuarder put(String namespace, GuardianState state) {
            guardianStates.put(new NamespacedInstanceId(namespace, InstanceId.of(namespace + "-instance", false)), state);
            return this;
        }

        @Override
        public Map<NamespacedInstanceId, GuardianState> getGuardianStates() {
            return guardianStates;
        }

        @Override
        public void register(String namespace, InstanceId instanceId) {
        }

        @Override
        public void unregister(String namespace, InstanceId instanceId) {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public boolean isRunning() {
            return true;
        }
    }
}
