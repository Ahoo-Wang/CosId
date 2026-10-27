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

package me.ahoo.cosid.store.zookeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.lease.AcquireReason;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;

import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Acquire reads every slot of the namespace in one asynchronous batch (06-stores.md §2.4): with 1024 slots, 1023 of
 * them held, it still finds the free one quickly. P10 measures it; this checks the result and keeps a loose bound.
 */
class ZookeeperAcquireManySlotsTest {
    private static final int LIMIT = 1024;
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Duration TOLERANCE = Duration.ofSeconds(1);

    @Test
    void acquireFindsTheLastFreeSlotOf1024() throws Exception {
        try (CuratorFramework client = ZookeeperTests.newClient()) {
            ZookeeperStore store = new ZookeeperStore(client, ZookeeperTests.TIMEOUT);
            String namespace = "many-" + UUID.randomUUID();
            SlotLease first = assertInstanceOf(AcquireResult.Acquired.class,
                store.slots().acquire(namespace, new Owner("host-0", UUID.randomUUID()), LIMIT, TTL, TOLERANCE, OptionalInt.empty())).lease();
            assertEquals(0, first.slot());
            List<CompletableFuture<?>> creates = new ArrayList<>();
            for (int slot = 1; slot < LIMIT - 1; slot++) {
                byte[] data = SlotState.NEW.take(new Owner("host-" + slot, UUID.randomUUID()), 0, TTL.toMillis(), TOLERANCE.toMillis()).bytes();
                CompletableFuture<Integer> created = new CompletableFuture<>();
                client.create().inBackground((c, event) -> created.complete(event.getResultCode()))
                    .forPath(ZookeeperSlotLeaseStore.slotPath(namespace, slot), data);
                creates.add(created);
            }
            for (CompletableFuture<?> created : creates) {
                assertEquals(KeeperException.Code.OK.intValue(), created.get());
            }

            long[] nanos = new long[5];
            for (int i = 0; i < nanos.length; i++) {
                Owner owner = new Owner("host-new", UUID.randomUUID());
                long start = System.nanoTime();
                AcquireResult.Acquired acquired = assertInstanceOf(AcquireResult.Acquired.class,
                    store.slots().acquire(namespace, owner, LIMIT, TTL, TOLERANCE, OptionalInt.empty()));
                nanos[i] = System.nanoTime() - start;
                assertEquals(LIMIT - 1, acquired.lease().slot());
                assertEquals(i == 0 ? AcquireReason.LOWEST : AcquireReason.STICKY, acquired.reason());
                assertInstanceOf(AcquireResult.Exhausted.class,
                    store.slots().acquire(namespace, new Owner("host-x", UUID.randomUUID()), LIMIT, TTL, TOLERANCE, OptionalInt.empty()));
                assertTrue(store.slots().release(acquired.lease(), acquired.lease().ceiling()));
            }
            Arrays.sort(nanos);
            long medianMillis = nanos[nanos.length / 2] / 1_000_000;
            System.out.println("acquire with " + LIMIT + " slots: median " + medianMillis + " ms, max " + nanos[nanos.length - 1] / 1_000_000 + " ms");
            assertTrue(medianMillis < 5_000, "acquire of 1024 slots took " + medianMillis + " ms");
        }
    }
}
