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

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.util.CallTimeout;

import lombok.SneakyThrows;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.framework.recipes.atomic.PromotedToLock;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
        ZookeeperIdSegmentDistributor distributor = new ZookeeperIdSegmentDistributor("cas", name, offset, 10, curatorFramework, retryPolicy,
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
    
    @Test
    void concurrentFetchesNextToOlderVersionsNeverOverlap() throws Exception {
        ZookeeperIdSegmentDistributor distributor = distributor("mixed-" + UUID.randomUUID(), 0);
        DistributedAtomicLong legacy = legacy(distributor);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            Set<Long> maxIds = ConcurrentHashMap.newKeySet();
            List<Future<Boolean>> fetches = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                boolean old = i % 4 == 0;
                fetches.add(executor.submit(() -> maxIds.add(old ? legacy.add(10L).postValue() : distributor.nextMaxId(10))));
            }
            for (Future<Boolean> fetch : fetches) {
                assertThat(fetch.get(60, TimeUnit.SECONDS), equalTo(true));
            }
            assertThat(maxIds.size(), equalTo(64));
            assertThat(maxIds.stream().mapToLong(Long::longValue).max().orElseThrow(), equalTo(640L));
        } finally {
            executor.shutdownNow();
        }
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
