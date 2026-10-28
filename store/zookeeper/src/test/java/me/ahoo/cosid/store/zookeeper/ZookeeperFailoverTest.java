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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.segment.Range;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.test.InstanceSpec;
import org.apache.curator.test.TestingCluster;
import org.apache.zookeeper.client.FourLetterWordMain;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A leader failover in a three-server ensemble (05 §3: safety must not depend on the store clock or the server that
 * stamps a write): ranges reserved across it never overlap, the holder of a lease keeps it with its fence, and the
 * next holder starts at or above the last ceiling written before or after the failover.
 */
class ZookeeperFailoverTest {
    private static final Duration TOLERANCE = Duration.ofSeconds(1);

    @Test
    void safetyHoldsAcrossALeaderFailover() throws Exception {
        try (TestingCluster cluster = new TestingCluster(3)) {
            cluster.start();
            try (CuratorFramework client = ZookeeperTests.newClient(cluster.getConnectString());
                 CuratorFramework other = ZookeeperTests.newClient(cluster.getConnectString())) {
                ZookeeperStore store = new ZookeeperStore(client, Duration.ofSeconds(30));
                String namespace = "failover-" + UUID.randomUUID();
                String key = "failover/" + UUID.randomUUID();
                SlotLease a = assertInstanceOf(AcquireResult.Acquired.class,
                    store.slots().acquire(namespace, new Owner("host-a", UUID.randomUUID()), 1, Duration.ofSeconds(3), TOLERANCE, 0, OptionalInt.empty()))
                    .lease();

                List<Range> ranges = new ArrayList<>();
                AtomicBoolean stop = new AtomicBoolean();
                Thread reserver = new Thread(() -> {
                    while (!stop.get()) {
                        try {
                            Range range = store.ranges().reserve(key, 10, 0);
                            synchronized (ranges) {
                                ranges.add(range);
                            }
                        } catch (CosIdException e) {
                            // a reservation during the election: a gap at most (S4).
                        }
                    }
                });
                reserver.start();
                while (rangeCount(ranges) < 20) {
                    Thread.sleep(10);
                }
                InstanceSpec leader = leader(cluster);
                assertTrue(cluster.killServer(leader), "killed the leader " + leader);
                int beforeFailover = rangeCount(ranges);

                SlotLease renewed = renewUntilAccepted(store, a);
                assertEquals(a.fence(), renewed.fence(), "the holder keeps its lease across the failover");
                assertTrue(renewed.ceiling() >= a.ceiling());
                long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
                while (rangeCount(ranges) < beforeFailover + 20 && System.nanoTime() < deadline) {
                    Thread.sleep(10);
                }
                stop.set(true);
                reserver.join();
                assertTrue(ranges.size() >= beforeFailover + 20, "reservations go on after the failover");
                for (int i = 1; i < ranges.size(); i++) {
                    assertTrue(ranges.get(i).start() >= ranges.get(i - 1).end(), "ranges strictly increase: " + ranges.get(i - 1) + ", " + ranges.get(i));
                }
                List<Range> sorted = new ArrayList<>(ranges);
                sorted.sort(Comparator.comparingLong(Range::start));
                assertEquals(ranges, sorted);

                // The holder stops renewing; the next one takes over on the new leader's clock.
                ZookeeperStore otherStore = new ZookeeperStore(other, Duration.ofSeconds(30));
                Owner b = new Owner("host-b", UUID.randomUUID());
                AcquireResult result;
                deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
                do {
                    result = otherStore.slots().acquire(namespace, b, 1, Duration.ofSeconds(30), TOLERANCE, 0, OptionalInt.empty());
                    Thread.sleep(100);
                } while (result instanceof AcquireResult.Exhausted && System.nanoTime() < deadline);
                SlotLease next = assertInstanceOf(AcquireResult.Acquired.class, result).lease();
                assertTrue(next.floor() >= renewed.ceiling(), "the next floor " + next.floor() + " is at least the last ceiling " + renewed.ceiling());
                assertTrue(next.fence() > renewed.fence());
                assertFalse(store.slots().renew(renewed, Duration.ofSeconds(3), TOLERANCE).isPresent());
            }
        }
    }

    private static int rangeCount(List<Range> ranges) {
        synchronized (ranges) {
            return ranges.size();
        }
    }

    private static SlotLease renewUntilAccepted(ZookeeperStore store, SlotLease lease) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (true) {
            try {
                Optional<SlotLease> renewed = store.slots().renew(lease, Duration.ofSeconds(3), TOLERANCE);
                return renewed.orElseThrow(() -> new AssertionError("the lease was lost in the failover"));
            } catch (CosIdException e) {
                if (System.nanoTime() > deadline) {
                    throw e;
                }
                Thread.sleep(100);
            }
        }
    }

    private static InstanceSpec leader(TestingCluster cluster) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            for (InstanceSpec instance : cluster.getInstances()) {
                String status = FourLetterWordMain.send4LetterWord(instance.getHostname(), instance.getPort(), "srvr");
                if (status.contains("Mode: leader")) {
                    return instance;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("no leader in " + cluster.getConnectString());
    }
}
