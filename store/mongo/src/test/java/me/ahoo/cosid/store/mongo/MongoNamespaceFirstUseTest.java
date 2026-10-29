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

package me.ahoo.cosid.store.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import me.ahoo.cosid.NamespaceMismatchException;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Many clients using a fresh namespace at once: the first-use upsert of {@code cosid_namespace}, the slot
 * provisioning and the index creation all race. A losing upsert may get E11000 (the server does not retry it in every
 * case), so the store retries and reads the winner back; each round uses new store instances, so nothing is cached between rounds.
 */
class MongoNamespaceFirstUseTest {
    private static final int CLIENTS = 4;
    private static final int THREADS = 16;
    private static final int ROUNDS = 30;
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final Duration TOLERANCE = Duration.ofSeconds(1);

    private static final List<MongoClient> clients = new ArrayList<>();
    private static ExecutorService executor;

    @BeforeAll
    static void open() {
        for (int i = 0; i < CLIENTS; i++) {
            clients.add(MongoClients.create(MongoTestSupport.uri()));
        }
        executor = Executors.newFixedThreadPool(THREADS);
    }

    @AfterAll
    static void close() {
        executor.shutdownNow();
        clients.forEach(MongoClient::close);
    }

    private static List<MongoSlotLeaseStore> newStores() {
        List<MongoSlotLeaseStore> stores = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            stores.add(new MongoSlotLeaseStore(clients.get(i % CLIENTS).getDatabase(MongoTestSupport.DATABASE), MongoTestSupport.TIMEOUT));
        }
        return stores;
    }

    /** Runs one call per store at once; returns each result or the exception it threw. */
    private static List<Object> race(List<Callable<Object>> calls) throws InterruptedException {
        CyclicBarrier barrier = new CyclicBarrier(calls.size());
        List<Callable<Object>> started = new ArrayList<>();
        for (Callable<Object> call : calls) {
            started.add(() -> {
                barrier.await();
                try {
                    return call.call();
                } catch (RuntimeException e) {
                    return e;
                }
            });
        }
        List<Object> outcomes = new ArrayList<>();
        for (Future<Object> future : executor.invokeAll(started)) {
            try {
                outcomes.add(future.get());
            } catch (ExecutionException e) {
                fail(e.getCause());
            }
        }
        return outcomes;
    }

    @Test
    void concurrentFirstUseGivesDistinctSlotsAndNoErrors() throws InterruptedException {
        int limit = THREADS / 2;
        for (int round = 0; round < ROUNDS; round++) {
            String namespace = "first-use-" + UUID.randomUUID();
            List<Callable<Object>> calls = new ArrayList<>();
            for (MongoSlotLeaseStore store : newStores()) {
                calls.add(() -> store.acquire(namespace, new Owner("host", UUID.randomUUID()), limit, TTL, TOLERANCE, 0, OptionalInt.empty()));
            }
            List<Object> outcomes = race(calls);
            Set<Integer> slots = new HashSet<>();
            int exhausted = 0;
            for (Object outcome : outcomes) {
                if (outcome instanceof AcquireResult.Acquired acquired) {
                    assertTrue(slots.add(acquired.lease().slot()), () -> "slot acquired twice: " + outcomes);
                } else {
                    assertInstanceOf(AcquireResult.Exhausted.class, outcome, () -> "round " + namespace + ": " + outcomes);
                    exhausted++;
                }
            }
            assertEquals(limit, slots.size(), () -> "every slot is taken: " + outcomes);
            assertEquals(THREADS - limit, exhausted);
        }
    }

    @Test
    void concurrentFirstUseWithOtherParametersKeepsTheWinners() throws InterruptedException {
        for (int round = 0; round < ROUNDS; round++) {
            String namespace = "first-use-" + UUID.randomUUID();
            List<Callable<Object>> calls = new ArrayList<>();
            List<MongoSlotLeaseStore> stores = newStores();
            for (int i = 0; i < THREADS; i++) {
                MongoSlotLeaseStore store = stores.get(i);
                int limit = i % 2 == 0 ? 32 : 64;
                calls.add(() -> {
                    store.acquire(namespace, new Owner("host", UUID.randomUUID()), limit, TTL, TOLERANCE, 0, OptionalInt.empty());
                    return limit;
                });
            }
            List<Object> outcomes = race(calls);
            Set<Object> accepted = new HashSet<>();
            for (Object outcome : outcomes) {
                if (outcome instanceof Integer limit) {
                    accepted.add(limit);
                } else {
                    assertInstanceOf(NamespaceMismatchException.class, outcome, () -> "round " + namespace + ": " + outcomes);
                }
            }
            assertEquals(1, accepted.size(), () -> "exactly the first recorded limit is accepted: " + outcomes);
        }
    }
}
