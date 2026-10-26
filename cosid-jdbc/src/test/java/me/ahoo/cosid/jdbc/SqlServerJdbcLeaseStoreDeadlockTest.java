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

package me.ahoo.cosid.jdbc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.lease.Lease;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.Stream;
import javax.sql.DataSource;

/**
 * Concurrent claims on a real SQL Server, where the scan of the candidates and the conditional updates of other
 * connections deadlock: SQL Server rolls one transaction back (error 1205) and the claim must try again, not fail.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqlServerJdbcLeaseStoreDeadlockTest {
    private static final int CONCURRENCY = 16;
    private static final int ROUNDS = 10;
    private static final long T0 = 1_000_000L;
    private static final int MAX_MACHINE_ID = 1023;

    /**
     * A connection per claimer, and claimers sharing a pool of four connections.
     */
    List<JdbcMachineLeaseStore> stores;

    @BeforeAll
    void setup() {
        DataSource dataSource = RealSqlServer.createDataSource();
        stores = List.of(DialectTables.leaseStore(dataSource, JdbcDialect.SQL_SERVER),
            DialectTables.leaseStore(PooledDataSource.of(dataSource, true), JdbcDialect.SQL_SERVER));
    }

    static Stream<Integer> stores() {
        return Stream.of(0, 1);
    }

    @ParameterizedTest
    @MethodSource("stores")
    void concurrentClaimersOfReleasedIdsAllSucceed(int storeIndex) throws Exception {
        JdbcMachineLeaseStore store = stores.get(storeIndex);
        for (int round = 0; round < ROUNDS; round++) {
            String namespace = "deadlockReleased_" + MockIdGenerator.INSTANCE.generateAsString();
            for (int i = 0; i < CONCURRENCY; i++) {
                store.release(store.claimNew(namespace, instance("owner" + i), MAX_MACHINE_ID, T0).orElseThrow(), T0 + 10);
            }

            assertDistinctWinners(concurrently(index ->
                () -> store.claimReleased(namespace, instance("claimer" + index), MAX_MACHINE_ID, T0 + 20)));
        }
    }

    @ParameterizedTest
    @MethodSource("stores")
    void concurrentClaimersOfExpiredIdsAllSucceed(int storeIndex) throws Exception {
        JdbcMachineLeaseStore store = stores.get(storeIndex);
        for (int round = 0; round < ROUNDS; round++) {
            String namespace = "deadlockExpired_" + MockIdGenerator.INSTANCE.generateAsString();
            for (int i = 0; i < CONCURRENCY; i++) {
                store.claimNew(namespace, instance("owner" + i), MAX_MACHINE_ID, T0).orElseThrow();
            }

            assertDistinctWinners(concurrently(index ->
                () -> store.claimExpired(namespace, instance("claimer" + index), MAX_MACHINE_ID, T0 + 100, T0 + 200)));
        }
    }

    private static InstanceId instance(String name) {
        return InstanceId.of(name, false);
    }

    private static void assertDistinctWinners(List<Optional<Lease>> results) {
        Set<Integer> machineIds = new HashSet<>();
        for (Optional<Lease> result : results) {
            assertThat("every claimer gets one of the free machine ids", result.isPresent(), equalTo(true));
            machineIds.add(result.get().getMachineId());
        }
        assertThat(machineIds, hasSize(CONCURRENCY));
    }

    private static List<Optional<Lease>> concurrently(IntFunction<Callable<Optional<Lease>>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Optional<Lease>>> futures = new ArrayList<>();
            for (int i = 0; i < CONCURRENCY; i++) {
                Callable<Optional<Lease>> task = tasks.apply(i);
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<Optional<Lease>> results = new ArrayList<>();
            for (Future<Optional<Lease>> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
