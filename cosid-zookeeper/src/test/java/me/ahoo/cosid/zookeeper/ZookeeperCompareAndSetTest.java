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

package me.ahoo.cosid.zookeeper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.nullValue;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.util.CallTimeout;

import lombok.SneakyThrows;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.framework.recipes.atomic.PromotedToLock;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The compare-and-set segment counter next to 3.x and 4.0 ({@code DistributedAtomicLong}), and the claim of an expired
 * lease among many machine ids, on an embedded ZooKeeper.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZookeeperCompareAndSetTest {
    TestingServer testingServer;
    CuratorFramework curatorFramework;
    RetryPolicy retryPolicy = new ExponentialBackoffRetry(10, 5, 100);
    
    @SneakyThrows
    @BeforeAll
    void setup() {
        testingServer = new TestingServer();
        curatorFramework = CuratorFrameworkFactory.newClient(testingServer.getConnectString(), new RetryNTimes(1, 10));
        curatorFramework.start();
        if (!curatorFramework.blockUntilConnected(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("CuratorFramework did not connect to TestingServer: " + testingServer.getConnectString());
        }
    }
    
    @SneakyThrows
    @AfterAll
    void destroy() {
        curatorFramework.close();
        testingServer.close();
    }
    
    private ZookeeperIdSegmentDistributor distributor(String name, long offset) {
        return distributor(name, offset, retryPolicy);
    }
    
    private ZookeeperIdSegmentDistributor distributor(String name, long offset, RetryPolicy distributorRetryPolicy) {
        ZookeeperIdSegmentDistributor distributor = new ZookeeperIdSegmentDistributor("cas", name, offset, 10, curatorFramework, distributorRetryPolicy,
            CallTimeout.of(Duration.ofSeconds(10)));
        distributor.ensureOffset();
        return distributor;
    }
    
    private DistributedAtomicLong legacy(ZookeeperIdSegmentDistributor distributor) {
        String counterPath = "/" + CosId.COSID + "/" + distributor.getNamespacedName();
        PromotedToLock lock = PromotedToLock.builder().lockPath(counterPath + "-locker").timeout(15, TimeUnit.SECONDS).retryPolicy(retryPolicy).build();
        return new DistributedAtomicLong(curatorFramework, counterPath, retryPolicy, lock);
    }
    
    @SneakyThrows
    @Test
    void theCounterIsTheEncodingOfOlderVersions() {
        ZookeeperIdSegmentDistributor distributor = distributor("encoding-" + UUID.randomUUID(), 7);
        DistributedAtomicLong legacy = legacy(distributor);
        assertThat(legacy.get().postValue(), equalTo(7L));
        
        assertThat(distributor.nextMaxId(10), equalTo(17L));
        assertThat(legacy.get().postValue(), equalTo(17L));
        assertThat(legacy.add(10L).postValue(), equalTo(27L));
        assertThat(distributor.nextMaxId(10), equalTo(37L));
    }
    
    /**
     * Fetches from {@code threads} threads at once, behind one start gate, each fetch by {@code fetch}.
     */
    private static Set<Long> fetchAtOnce(int fetches, java.util.function.IntFunction<java.util.concurrent.Callable<Long>> fetch) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(16);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Long>> results = new ArrayList<>();
            for (int i = 0; i < fetches; i++) {
                java.util.concurrent.Callable<Long> call = fetch.apply(i);
                results.add(executor.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            Set<Long> maxIds = new HashSet<>();
            for (Future<Long> result : results) {
                maxIds.add(result.get(60, TimeUnit.SECONDS));
            }
            return maxIds;
        } finally {
            executor.shutdownNow();
        }
    }
    
    /**
     * 3.x and 4.0 nodes: {@code DistributedAtomicLong} reports a give-up instead of throwing; they fetch again.
     */
    private static long legacyFetch(DistributedAtomicLong legacy, long step) throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            AtomicValue<Long> added = legacy.add(step);
            if (added.succeeded()) {
                return added.postValue();
            }
        }
        throw new AssertionError("The 3.x/4.0 client gave up 20 times in a row.");
    }
    
    @RepeatedTest(20)
    void concurrentFetchesNextToOlderVersionsNeverOverlap() throws Exception {
        // The 4.1 distributor does not use the retry policy for conflicts: none at all.
        ZookeeperIdSegmentDistributor distributor = distributor("mixed-" + UUID.randomUUID(), 0, new RetryNTimes(0, 0));
        DistributedAtomicLong legacy = legacy(distributor);
        
        Set<Long> maxIds = fetchAtOnce(64, i -> i % 4 == 0 ? () -> legacyFetch(legacy, 10) : () -> distributor.nextMaxId(10));
        
        assertThat(maxIds.size(), equalTo(64));
        assertThat(maxIds.stream().mapToLong(Long::longValue).max().orElseThrow(), equalTo(640L));
    }
    
    @RepeatedTest(20)
    void conflictsAreRetriedWhateverTheRetryPolicy() throws Exception {
        ZookeeperIdSegmentDistributor distributor = distributor("contended-" + UUID.randomUUID(), 0, new RetryNTimes(0, 0));
        
        Set<Long> maxIds = fetchAtOnce(64, i -> () -> distributor.nextMaxId(10));
        
        assertThat(maxIds.size(), equalTo(64));
        assertThat(maxIds.stream().mapToLong(Long::longValue).max().orElseThrow(), equalTo(640L));
    }
    
    @SneakyThrows
    @Test
    void anUncontendedFetchIsOneVersionedWriteWithoutTheLock() {
        ZookeeperIdSegmentDistributor distributor = distributor("uncontended-" + UUID.randomUUID(), 0, retryPolicy);
        String counterPath = "/" + CosId.COSID + "/" + distributor.getNamespacedName();
        int version = curatorFramework.checkExists().forPath(counterPath).getVersion();
        
        for (int i = 1; i <= 5; i++) {
            distributor.nextMaxId(10);
            assertThat(curatorFramework.checkExists().forPath(counterPath).getVersion(), equalTo(version + i));
        }
        
        assertThat(curatorFramework.checkExists().forPath(distributor.getCounterLockerPath()), nullValue());
    }
    
    @Test
    void anExpiredLeaseAmong1024IsClaimedQuickly() {
        ZookeeperMachineLeaseStore store = new ZookeeperMachineLeaseStore(curatorFramework, CallTimeout.of(Duration.ofSeconds(20)));
        String namespace = "scale-" + UUID.randomUUID();
        for (int i = 0; i < 1024; i++) {
            store.claimNew(namespace, InstanceId.of("holder-" + i, false), 1023, 1000);
        }
        
        long started = System.nanoTime();
        assertThat(store.claimExpired(namespace, InstanceId.of("taker", false), 1023, 1000, 2000).isPresent(), equalTo(true));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        
        assertThat(elapsedMillis, lessThan(10_000L));
    }
}
