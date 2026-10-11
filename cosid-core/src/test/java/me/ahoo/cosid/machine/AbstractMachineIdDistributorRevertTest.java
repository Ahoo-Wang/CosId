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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import me.ahoo.cosid.CosIdException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class AbstractMachineIdDistributorRevertTest {
    private static final String NAMESPACE = "AbstractMachineIdDistributorRevertTest";
    private static final InstanceId INSTANCE_ID = InstanceId.of("host", 1, false);

    @Test
    void revertShouldClearLocalStateWhenRemoteRevertFails() {
        MachineStateStorage storage = new InMemoryMachineStateStorage();
        storage.set(NAMESPACE, 3, INSTANCE_ID);
        AbstractMachineIdDistributor distributor = new AbstractMachineIdDistributor(storage, ClockBackwardsSynchronizer.DEFAULT) {
            @Override
            protected MachineState distributeRemote(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void revertRemote(String namespace, InstanceId instanceId, MachineState machineState) {
                // e.g. a timed-out command that may still have been applied remotely
                throw new CosIdException("revert timed out");
            }

            @Override
            protected void guardRemote(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) {
                throw new UnsupportedOperationException();
            }
        };

        Assertions.assertThrows(CosIdException.class, () -> distributor.revert(NAMESPACE, INSTANCE_ID));

        assertThat(storage.get(NAMESPACE, INSTANCE_ID), equalTo(MachineState.NOT_FOUND));
    }
}
