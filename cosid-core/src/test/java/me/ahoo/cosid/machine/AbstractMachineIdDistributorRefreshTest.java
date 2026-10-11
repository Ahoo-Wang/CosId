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
import static org.hamcrest.Matchers.greaterThan;

import org.junit.jupiter.api.Test;

import java.time.Duration;

class AbstractMachineIdDistributorRefreshTest {
    private static final String NAMESPACE = "AbstractMachineIdDistributorRefreshTest";
    private static final InstanceId INSTANCE_ID = InstanceId.of("host", 1, false);
    
    private final InMemoryMachineStateStorage storage = new InMemoryMachineStateStorage();
    private final ManualMachineIdDistributor distributor = new ManualMachineIdDistributor(3, storage, ClockBackwardsSynchronizer.DEFAULT);
    
    @Test
    void refreshShouldMoveLocalTimestampToNow() throws InterruptedException {
        distributor.distribute(NAMESPACE, 10, INSTANCE_ID, Duration.ofMinutes(5));
        long distributedAt = storage.get(NAMESPACE, INSTANCE_ID).getLastTimeStamp();
        Thread.sleep(20);
        
        distributor.refreshLocalState(NAMESPACE, INSTANCE_ID);
        
        MachineState refreshed = storage.get(NAMESPACE, INSTANCE_ID);
        assertThat(refreshed.getMachineId(), equalTo(3));
        assertThat(refreshed.getLastTimeStamp(), greaterThan(distributedAt));
    }
    
    @Test
    void refreshShouldNotMoveLocalTimestampBackwards() {
        long future = System.currentTimeMillis() + Duration.ofMinutes(1).toMillis();
        MachineStateStorage futureStorage = new InMemoryMachineStateStorage() {
            @Override
            public MachineState get(String namespace, InstanceId instanceId) {
                return MachineState.of(3, future);
            }
            
            @Override
            public void set(String namespace, int machineId, InstanceId instanceId) {
                throw new AssertionError("must not move the watermark backwards");
            }
        };
        
        new ManualMachineIdDistributor(3, futureStorage, ClockBackwardsSynchronizer.DEFAULT).refreshLocalState(NAMESPACE, INSTANCE_ID);
    }
    
    @Test
    void refreshShouldNotBringBackRevertedState() {
        distributor.distribute(NAMESPACE, 10, INSTANCE_ID, Duration.ofMinutes(5));
        distributor.revert(NAMESPACE, INSTANCE_ID);
        
        distributor.refreshLocalState(NAMESPACE, INSTANCE_ID);
        
        assertThat(storage.get(NAMESPACE, INSTANCE_ID), equalTo(MachineState.NOT_FOUND));
    }
}
