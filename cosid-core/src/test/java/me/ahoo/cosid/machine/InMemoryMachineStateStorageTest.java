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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class InMemoryMachineStateStorageTest {

    private static final String NAMESPACE = "core";
    private final InMemoryMachineStateStorage storage = new InMemoryMachineStateStorage();

    @Test
    void getShouldReturnNotFoundSentinelWhenStateDoesNotExist() {
        assertSame(MachineState.NOT_FOUND, storage.get(NAMESPACE, InstanceId.NONE));
        assertFalse(storage.exists(NAMESPACE, InstanceId.NONE));
        assertEquals(0, storage.size(NAMESPACE));
    }

    @Test
    void setShouldCreateAndOverwriteStateForSameNamespacedInstance() {
        InstanceId instanceId = InstanceId.of("host-a:8080", false);

        storage.set(NAMESPACE, 1, instanceId);
        storage.set(NAMESPACE, 2, instanceId);

        MachineState state = storage.get(NAMESPACE, instanceId);
        assertEquals(2, state.getMachineId());
        assertTrue(state.getLastTimeStamp() > 0);
        assertTrue(storage.exists(NAMESPACE, instanceId));
        assertEquals(1, storage.size(NAMESPACE));
    }

    @Test
    void removeShouldOnlyDeleteTheExactNamespacedInstance() {
        InstanceId target = InstanceId.of("same-id", false);
        InstanceId sameTextDifferentStability = InstanceId.of("same-id", true);
        storage.set(NAMESPACE, 1, target);
        storage.set(NAMESPACE, 2, sameTextDifferentStability);

        storage.remove(NAMESPACE, target);

        assertSame(MachineState.NOT_FOUND, storage.get(NAMESPACE, target));
        assertEquals(2, storage.get(NAMESPACE, sameTextDifferentStability).getMachineId());
        assertEquals(1, storage.size(NAMESPACE));
    }

    @Test
    void clearShouldMatchNamespaceExactlyEvenWhenDelimiterAppearsInNamespace() {
        InstanceId instanceId = InstanceId.of("instance", false);
        storage.set("tenant", 1, instanceId);
        storage.set("tenant__blue", 2, instanceId);

        storage.clear("tenant");

        assertFalse(storage.exists("tenant", instanceId));
        assertEquals(2, storage.get("tenant__blue", instanceId).getMachineId());
        assertEquals(0, storage.size("tenant"));
        assertEquals(1, storage.size("tenant__blue"));
    }

    @Test
    void concurrentSetShouldKeepOneStatePerDistinctInstance() throws Exception {
        int workers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int machineId = 0; machineId < workers; machineId++) {
            int currentMachineId = machineId;
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                storage.set(NAMESPACE, currentMachineId, InstanceId.of("instance-" + currentMachineId, false));
                return null;
            }));
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdownNow();

        assertEquals(workers, storage.size(NAMESPACE));
        for (int machineId = 0; machineId < workers; machineId++) {
            assertEquals(machineId, storage.get(NAMESPACE, InstanceId.of("instance-" + machineId, false)).getMachineId());
        }
    }
}
