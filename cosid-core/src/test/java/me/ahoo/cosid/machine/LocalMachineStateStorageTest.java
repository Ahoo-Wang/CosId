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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

class LocalMachineStateStorageTest {

    @TempDir
    Path stateDirectory;

    @Test
    void getShouldReturnNotFoundWhenStateFileDoesNotExistOrIsEmpty() throws IOException {
        LocalMachineStateStorage storage = storage();
        InstanceId instanceId = InstanceId.of("instance-a", false);

        assertSame(MachineState.NOT_FOUND, storage.get("namespace", instanceId));

        Files.writeString(stateFile("namespace", instanceId), "", StandardCharsets.UTF_8);

        assertSame(MachineState.NOT_FOUND, storage.get("namespace", instanceId));
    }

    @Test
    void setShouldPersistReadableStateAndOverwriteSameInstance() {
        LocalMachineStateStorage storage = storage();
        InstanceId instanceId = InstanceId.of("instance-a", false);

        storage.set("namespace", 1, instanceId);
        storage.set("namespace", 2, instanceId);

        MachineState state = storage.get("namespace", instanceId);
        assertEquals(2, state.getMachineId());
        assertTrue(state.getLastTimeStamp() > 0);
        assertTrue(storage.exists("namespace", instanceId));
        assertEquals(1, storage.size("namespace"));
    }

    @Test
    void removeShouldDeleteOnlyExactNamespacedInstanceFile() {
        LocalMachineStateStorage storage = storage();
        InstanceId target = InstanceId.of("same-id", false);
        InstanceId sameTextDifferentStability = InstanceId.of("same-id", true);
        storage.set("namespace", 1, target);
        storage.set("namespace", 2, sameTextDifferentStability);

        storage.remove("namespace", target);

        assertSame(MachineState.NOT_FOUND, storage.get("namespace", target));
        assertEquals(2, storage.get("namespace", sameTextDifferentStability).getMachineId());
        assertEquals(1, storage.size("namespace"));
    }

    @Test
    void clearShouldMatchNamespaceExactlyWhenNamespaceContainsDelimiter() {
        LocalMachineStateStorage storage = storage();
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
    void sizeAndClearShouldIgnoreMalformedStateFileNames() throws IOException {
        LocalMachineStateStorage storage = storage();
        InstanceId instanceId = InstanceId.of("instance", false);
        Files.writeString(stateDirectory.resolve("not-base64"), "ignored", StandardCharsets.UTF_8);
        Files.writeString(stateDirectory.resolve(encode("namespace")), "ignored", StandardCharsets.UTF_8);
        storage.set("namespace", 1, instanceId);

        assertEquals(1, storage.size("namespace"));

        storage.clear("namespace");

        assertEquals(0, storage.size("namespace"));
        assertTrue(Files.exists(stateDirectory.resolve("not-base64")));
        assertTrue(Files.exists(stateDirectory.resolve(encode("namespace"))));
    }

    @Test
    void operationsShouldRejectInvalidArgumentsWithContractMessages() {
        LocalMachineStateStorage storage = storage();
        InstanceId instanceId = InstanceId.of("instance", false);

        assertEquals("namespace can not be empty!",
            assertThrows(IllegalArgumentException.class, () -> storage.get("", instanceId)).getMessage());
        assertEquals("instanceId can not be null!",
            assertThrows(NullPointerException.class, () -> storage.get("namespace", null)).getMessage());
        assertEquals("machineId:[-1] must be greater than or equal to 0!",
            assertThrows(IllegalArgumentException.class, () -> storage.set("namespace", -1, instanceId)).getMessage());
    }

    private LocalMachineStateStorage storage() {
        return new LocalMachineStateStorage(stateDirectory.toString());
    }

    private Path stateFile(String namespace, InstanceId instanceId) {
        return stateDirectory.resolve(encode(namespace + "__" + instanceId.getInstanceId()));
    }

    private static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
