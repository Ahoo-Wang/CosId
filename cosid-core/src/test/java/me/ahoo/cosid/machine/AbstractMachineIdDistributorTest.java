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

import me.ahoo.cosid.snowflake.exception.ClockTooManyBackwardsException;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class AbstractMachineIdDistributorTest {
    private static final InstanceId INSTANCE_ID = InstanceId.of("instance", false);
    private static final Duration SAFE_GUARD_DURATION = Duration.ofMinutes(5);

    private static String namespace() {
        return MockIdGenerator.INSTANCE.generateAsString();
    }

    @Test
    void distributeShouldValidateLocalStateRemotely() {
        String namespace = namespace();
        MachineStateStorage storage = new InMemoryMachineStateStorage();
        storage.set(namespace, 0, INSTANCE_ID);
        RecordingDistributor distributor = new RecordingDistributor(storage, MachineState.of(1, AbstractMachineIdDistributor.NOT_FOUND_LAST_STAMP));

        MachineState actual = distributor.distribute(namespace, 2, INSTANCE_ID, SAFE_GUARD_DURATION);

        Assertions.assertEquals(1, actual.getMachineId());
        Assertions.assertEquals(1, distributor.distributeRemoteCalls);
        Assertions.assertEquals(1, storage.get(namespace, INSTANCE_ID).getMachineId());
    }

    @Test
    void distributeShouldWaitForLocalWatermarkOfSameMachineId() {
        long localWatermark = System.currentTimeMillis() + 100;
        RecordingDistributor distributor = new RecordingDistributor(new FixedStateStorage(MachineState.of(0, localWatermark)),
            MachineState.of(0, AbstractMachineIdDistributor.NOT_FOUND_LAST_STAMP));

        MachineState actual = distributor.distribute(namespace(), 2, INSTANCE_ID, SAFE_GUARD_DURATION);

        Assertions.assertEquals(0, actual.getMachineId());
        Assertions.assertTrue(System.currentTimeMillis() >= localWatermark);
        Assertions.assertTrue(actual.getLastTimeStamp() >= localWatermark);
    }

    @Test
    void distributeShouldIgnoreWatermarkOfStaleLocalMachineId() {
        RecordingDistributor distributor = new RecordingDistributor(new FixedStateStorage(MachineState.of(0, System.currentTimeMillis() + 60_000)),
            MachineState.of(1, AbstractMachineIdDistributor.NOT_FOUND_LAST_STAMP));

        MachineState actual = distributor.distribute(namespace(), 2, INSTANCE_ID, SAFE_GUARD_DURATION);

        Assertions.assertEquals(1, actual.getMachineId());
        Assertions.assertEquals(0, distributor.revertRemoteCalls);
    }

    @Test
    void distributeShouldReleaseMachineIdWhenClockIsTooFarBehind() {
        RecordingDistributor distributor = new RecordingDistributor(new InMemoryMachineStateStorage(),
            MachineState.of(1, System.currentTimeMillis() + 60_000));
        String namespace = namespace();

        Assertions.assertThrows(ClockTooManyBackwardsException.class,
            () -> distributor.distribute(namespace, 2, INSTANCE_ID, SAFE_GUARD_DURATION));

        Assertions.assertEquals(1, distributor.revertRemoteCalls);
    }

    static class RecordingDistributor extends AbstractMachineIdDistributor {
        private final MachineState remoteState;
        int distributeRemoteCalls;
        int revertRemoteCalls;

        RecordingDistributor(MachineStateStorage machineStateStorage, MachineState remoteState) {
            super(machineStateStorage, ClockBackwardsSynchronizer.DEFAULT);
            this.remoteState = remoteState;
        }

        @Override
        protected MachineState distributeRemote(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
            distributeRemoteCalls++;
            return remoteState;
        }

        @Override
        protected void revertRemote(String namespace, InstanceId instanceId, MachineState machineState) {
            revertRemoteCalls++;
        }

        @Override
        protected void guardRemote(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) {
        }
    }

    static class FixedStateStorage extends InMemoryMachineStateStorage {
        private final MachineState state;

        FixedStateStorage(MachineState state) {
            this.state = state;
        }

        @Override
        public MachineState get(String namespace, InstanceId instanceId) {
            return state;
        }
    }
}
