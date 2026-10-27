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

package me.ahoo.cosid.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.store.remote.RemoteApi;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * C-M1: the key caps count per client, bound the namespaces (keys without {@code /} included), forget failed reserves
 * and evict idle keys.
 */
class KeyCapTest {
    private static final Grant ORDERS = new Grant("orders", false, Set.of("shared"), List.of());
    private static final Grant BILLING = new Grant("billing", false, Set.of("shared"), List.of());

    private final AtomicLong now = new AtomicLong();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private KeyCap cap(int keys, int namespaces, Duration idle) {
        return new KeyCap(keys, namespaces, idle, now::get, new Rejections(meters));
    }

    private static String rejected(KeyCap cap, Grant grant, String key) {
        ApiException error = assertThrows(ApiException.class, () -> cap.admit(grant, key));
        assertEquals(429, error.status().value());
        return error.code();
    }

    private double rejections(String reason) {
        return meters.get(Rejections.METER).tag("reason", reason).counter().count();
    }

    @Test
    void oneClientCannotUseUpASharedNamespaceForTheOthers() {
        KeyCap cap = cap(2, 0, Duration.ZERO);
        cap.admit(ORDERS, "shared/a");
        cap.admit(ORDERS, "shared/b");
        cap.admit(ORDERS, "shared/a");
        assertEquals(RemoteApi.TOO_MANY_KEYS, rejected(cap, ORDERS, "shared/c"));
        assertDoesNotThrow(() -> cap.admit(BILLING, "shared/c"));
        assertDoesNotThrow(() -> cap.admit(BILLING, "shared/d"));
        assertEquals(1, rejections(Rejections.TOO_MANY_KEYS));
    }

    @Test
    void keysWithoutASlashCountAsNamespaces() {
        KeyCap cap = cap(100, 3, Duration.ZERO);
        cap.admit(ORDERS, "a");
        cap.admit(ORDERS, "b");
        cap.admit(ORDERS, "c/1");
        cap.admit(ORDERS, "c/2");
        assertEquals(RemoteApi.TOO_MANY_NAMESPACES, rejected(cap, ORDERS, "d"));
        assertEquals(RemoteApi.TOO_MANY_NAMESPACES, rejected(cap, ORDERS, "e/1"));
        assertDoesNotThrow(() -> cap.admit(ORDERS, "c/3"), "a known namespace takes more keys");
        assertEquals(2, rejections(Rejections.TOO_MANY_NAMESPACES));
    }

    @Test
    void failedReservesAreForgotten() {
        KeyCap cap = cap(1, 1, Duration.ZERO);
        Runnable forget = cap.admit(ORDERS, "shared/a");
        forget.run();
        forget.run();
        assertDoesNotThrow(() -> cap.admit(ORDERS, "other/b"), "neither the key nor its namespace count after the failure");
        Runnable again = cap.admit(ORDERS, "other/b");
        again.run();
        assertEquals(RemoteApi.TOO_MANY_KEYS, rejected(cap, ORDERS, "other/c"),
            "a failed reserve of a key already counted does not forget it: other/b still fills the cap of 1");
    }

    @Test
    void idleKeysNoLongerCount() {
        KeyCap cap = cap(2, 0, Duration.ofMinutes(10));
        cap.admit(ORDERS, "shared/a");
        now.addAndGet(Duration.ofMinutes(6).toNanos());
        cap.admit(ORDERS, "shared/b");
        assertEquals(RemoteApi.TOO_MANY_KEYS, rejected(cap, ORDERS, "shared/c"));
        now.addAndGet(Duration.ofMinutes(5).toNanos());
        assertDoesNotThrow(() -> cap.admit(ORDERS, "shared/c"), "a, idle for 11 minutes, was evicted");
        cap.admit(ORDERS, "shared/b");
        now.addAndGet(Duration.ofMinutes(9).toNanos());
        assertEquals(RemoteApi.TOO_MANY_KEYS, rejected(cap, ORDERS, "shared/d"), "using b again kept it");
    }

    @Test
    void zeroCapsAdmitEveryKey() {
        KeyCap cap = cap(0, 0, Duration.ZERO);
        for (int i = 0; i < 100; i++) {
            cap.admit(ORDERS, "ns" + i);
        }
        KeyCap keysOnly = cap(0, 2, Duration.ZERO);
        for (int i = 0; i < 100; i++) {
            keysOnly.admit(ORDERS, "ns/" + i);
        }
    }
}
