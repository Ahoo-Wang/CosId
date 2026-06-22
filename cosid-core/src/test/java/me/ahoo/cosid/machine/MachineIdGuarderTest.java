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

package me.ahoo.cosid.machine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Map;

class MachineIdGuarderTest {

    @Test
    void noneShouldKeepEmptyStateAndNeverRun() {
        MachineIdGuarder.NONE.register("namespace", InstanceId.NONE);
        MachineIdGuarder.NONE.start();
        MachineIdGuarder.NONE.unregister("namespace", InstanceId.NONE);
        MachineIdGuarder.NONE.stop();

        assertTrue(MachineIdGuarder.NONE.getGuardianStates().isEmpty());
        assertFalse(MachineIdGuarder.NONE.hasFailure());
        assertFalse(MachineIdGuarder.NONE.isRunning());
    }

    @Test
    void hasFailureShouldReflectAnyFailedGuardianState() {
        NamespacedInstanceId ok = new NamespacedInstanceId("namespace", InstanceId.of("ok", false));
        NamespacedInstanceId failed = new NamespacedInstanceId("namespace", InstanceId.of("failed", false));
        MachineIdGuarder guarder = new StaticMachineIdGuarder(Map.of(
            ok, GuardianState.success(1L),
            failed, GuardianState.failed(2L, new IllegalStateException("lost"))
        ));

        assertTrue(guarder.hasFailure());
    }

    @Test
    void hasFailureShouldBeFalseWhenAllStatesAreInitialOrSuccessful() {
        NamespacedInstanceId initial = new NamespacedInstanceId("namespace", InstanceId.of("initial", false));
        NamespacedInstanceId success = new NamespacedInstanceId("namespace", InstanceId.of("success", false));
        MachineIdGuarder guarder = new StaticMachineIdGuarder(Map.of(
            initial, GuardianState.INITIAL,
            success, GuardianState.success(2L)
        ));

        assertFalse(guarder.hasFailure());
    }

    private static final class StaticMachineIdGuarder implements MachineIdGuarder {
        private final Map<NamespacedInstanceId, GuardianState> guardianStates;

        private StaticMachineIdGuarder(Map<NamespacedInstanceId, GuardianState> guardianStates) {
            this.guardianStates = guardianStates;
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
            return false;
        }
    }
}
