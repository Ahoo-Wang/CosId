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

package me.ahoo.cosid.event;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.internal.TimestampSequencer;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdLease;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.machine.lease.MachineLeaseManager;
import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.IdSegment;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import me.ahoo.cosid.snowflake.ClockSyncSnowflakeId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.test.machine.lease.InMemoryMachineLeaseStore;
import me.ahoo.cosid.test.segment.AtomicIdSegmentDistributor;
import me.ahoo.cosid.util.CallTimeout;
import me.ahoo.cosid.util.Clock;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class CosIdEventsTest {
    private final Recording recording = new Recording();
    private AutoCloseable registration;
    
    private void register() {
        registration = CosIdEvents.register(recording);
    }
    
    @AfterEach
    void unregister() throws Exception {
        if (registration != null) {
            registration.close();
        }
    }
    
    static final class Recording implements CosIdEventListener {
        final List<String> events = new CopyOnWriteArrayList<>();
        
        @Override
        public void onClockBackwards(String generator, long lastTimestamp, long currentTimestamp) {
            events.add("clockBackwards");
        }
        
        @Override
        public void onClockSyncWait(String generator, Duration waited) {
            events.add("clockSyncWait");
        }
        
        @Override
        public void onSequenceExhaustedWait(Duration waited) {
            events.add("sequenceExhaustedWait");
        }
        
        @Override
        public void onSegmentFetch(String namespacedName, Duration latency, @Nullable Throwable failure) {
            events.add(failure == null ? "segmentFetch" : "segmentFetchFailed");
        }
        
        @Override
        public void onPrefetchExpansion(String namespacedName, int fromDistance, int toDistance) {
            events.add("prefetchExpansion:" + fromDistance + "->" + toDistance);
        }
        
        @Override
        public void onPrefetchFailure(String namespacedName, Throwable failure) {
            events.add("prefetchFailure");
        }
        
        @Override
        public void onLeaseAcquired(String namespace, InstanceId instanceId, int machineId, LeaseClaim claim) {
            events.add("leaseAcquired:" + claim);
        }
        
        @Override
        public void onLeaseRenewed(String namespace, InstanceId instanceId, int machineId, long clockSkewMillis) {
            events.add("leaseRenewed");
        }
        
        @Override
        public void onLeaseRenewFailed(String namespace, InstanceId instanceId, Throwable failure) {
            events.add("leaseRenewFailed");
        }
        
        @Override
        public void onLeaseLost(String namespace, InstanceId instanceId) {
            events.add("leaseLost");
        }
        
        @Override
        public void onCallTimeout(String operation, Duration timeout) {
            events.add("callTimeout");
        }
        
        @Override
        public void onCallRejected(String operation) {
            events.add("callRejected");
        }
    }
    
    @Test
    void theNoOpListenerIsASingleton() throws Exception {
        assertThat(CosIdEvents.listener(), sameInstance(CosIdEventListener.NONE));
        register();
        registration.close();
        registration = null;
        assertThat(CosIdEvents.listener(), sameInstance(CosIdEventListener.NONE));
    }
    
    @Test
    void aFailingListenerNeverFailsTheCaller() throws Exception {
        try (AutoCloseable failing = CosIdEvents.register(new CosIdEventListener() {
            @Override
            public void onLeaseLost(String namespace, InstanceId instanceId) {
                throw new IllegalStateException("listener bug");
            }
        })) {
            register();
            new MachineIdLease("ns", InstanceId.of("i", false), Duration.ofMinutes(1)).markLost();
        }
        assertThat(recording.events, contains("leaseLost"));
    }
    
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable throwable) throws T {
        throw (T) throwable;
    }
    
    @Test
    void aListenerThrowingAnErrorNeverFailsTheCaller() throws Exception {
        try (AutoCloseable failing = CosIdEvents.register(new CosIdEventListener() {
            @Override
            public void onLeaseLost(String namespace, InstanceId instanceId) {
                throw new AssertionError("listener assertion");
            }
        })) {
            register();
            new MachineIdLease("ns", InstanceId.of("i", false), Duration.ofMinutes(1)).markLost();
        }
        assertThat(recording.events, contains("leaseLost"));
    }
    
    @Test
    void aListenerSneakyThrowingACheckedExceptionNeverFailsTheCaller() throws Exception {
        try (AutoCloseable failing = CosIdEvents.register(new CosIdEventListener() {
            @Override
            public void onLeaseLost(String namespace, InstanceId instanceId) {
                CosIdEventsTest.<RuntimeException>sneakyThrow(new java.io.IOException("checked"));
            }
        })) {
            register();
            new MachineIdLease("ns", InstanceId.of("i", false), Duration.ofMinutes(1)).markLost();
        }
        assertThat(recording.events, contains("leaseLost"));
    }
    
    @Test
    void aVirtualMachineErrorIsRethrown() throws Exception {
        try (AutoCloseable failing = CosIdEvents.register(new CosIdEventListener() {
            @Override
            public void onCallRejected(String operation) {
                throw new StackOverflowError("fatal");
            }
        })) {
            assertThrows(StackOverflowError.class, () -> CosIdEvents.listener().onCallRejected("op"));
        }
    }
    
    @Test
    void clockBackwardsAndTheClockSyncWait() {
        long[] times = {CosId.COSID_EPOCH + 1_000, CosId.COSID_EPOCH + 999, CosId.COSID_EPOCH + 1_001};
        int[] read = {0};
        MillisecondSnowflakeId actual = new MillisecondSnowflakeId(CosId.COSID_EPOCH, 41, 10, 12, 1, 100) {
            @Override
            protected long getCurrentTime() {
                return times[Math.min(read[0]++, times.length - 1)];
            }
        };
        ClockSyncSnowflakeId clockSync = new ClockSyncSnowflakeId(actual, ClockBackwardsSynchronizer.DEFAULT);
        clockSync.generate();
        register();
        clockSync.generate();
        assertThat(recording.events, contains("clockBackwards", "clockSyncWait"));
    }
    
    @Test
    void aSequenceExhaustedWait() {
        long[] times = {100, 100, 101};
        int[] read = {0};
        TimestampSequencer sequencer = new TimestampSequencer(() -> times[Math.min(read[0]++, times.length - 1)], 1, 100);
        sequencer.next();
        register();
        sequencer.next();
        assertThat(recording.events, contains("sequenceExhaustedWait"));
    }
    
    @Test
    void segmentFetches() {
        register();
        new DefaultSegmentId(new AtomicIdSegmentDistributor(10)).generate();
        IdSegmentDistributor broken = new AtomicIdSegmentDistributor(10) {
            @Override
            public long nextMaxId(long step) {
                throw new IllegalStateException("store down");
            }
        };
        assertThrows(IllegalStateException.class, () -> new DefaultSegmentId(broken).generate());
        assertThat(recording.events, contains("segmentFetch", "segmentFetchFailed"));
    }
    
    private static SegmentChainId.PrefetchJob prefetchJob(SegmentChainId chainId) throws ReflectiveOperationException {
        Field field = SegmentChainId.class.getDeclaredField("prefetchJob");
        field.setAccessible(true);
        return (SegmentChainId.PrefetchJob) field.get(chainId);
    }
    
    @Test
    void aStarvingPrefetchExpandsItsDistance() throws Exception {
        PrefetchWorkerExecutorService executor = new PrefetchWorkerExecutorService(Duration.ofSeconds(1), 1, false);
        try {
            SegmentChainId chainId = new SegmentChainId(IdSegment.TIME_TO_LIVE_FOREVER, 2, 8,
                new AtomicIdSegmentDistributor(10), executor);
            SegmentChainId.PrefetchJob job = prefetchJob(chainId);
            register();
            job.setHungerTime(Clock.SYSTEM.secondTime());
            job.prefetch();
            assertThat(recording.events, hasItem("prefetchExpansion:2->4"));
        } finally {
            executor.shutdown();
        }
    }
    
    @Test
    void aFailingPrefetch() throws Exception {
        PrefetchWorkerExecutorService executor = new PrefetchWorkerExecutorService(Duration.ofSeconds(1), 1, false);
        try {
            IdSegmentDistributor broken = new AtomicIdSegmentDistributor(10) {
                @Override
                public long nextMaxId(long step) {
                    throw new IllegalStateException("store down");
                }
            };
            SegmentChainId chainId = new SegmentChainId(IdSegment.TIME_TO_LIVE_FOREVER, 2, 8, broken, executor);
            register();
            prefetchJob(chainId).prefetch();
            assertThat(recording.events, hasItem("prefetchFailure"));
        } finally {
            executor.shutdown();
        }
    }
    
    @Test
    void leaseClaims() throws InterruptedException {
        InMemoryMachineLeaseStore store = new InMemoryMachineLeaseStore();
        MachineLeaseManager manager = new MachineLeaseManager(store);
        register();
        manager.acquire("ns", InstanceId.of("a", false), 1, Duration.ofMinutes(5));
        var again = manager.acquire("ns", InstanceId.of("a", false), 1, Duration.ofMinutes(5));
        manager.release(again, again.getWatermark());
        Thread.sleep(5);
        manager.acquire("ns", InstanceId.of("b", false), 1, Duration.ofMinutes(5));
        manager.acquire("ns", InstanceId.of("c", false), 1, Duration.ofMinutes(5));
        Thread.sleep(50);
        // Every machine id is held: d takes over a lease not renewed for longer than its safe guard.
        manager.acquire("ns", InstanceId.of("d", false), 1, Duration.ofMillis(1));
        assertThat(recording.events, contains("leaseAcquired:NEW", "leaseAcquired:SELF", "leaseAcquired:RELEASED", "leaseAcquired:NEW",
            "leaseAcquired:EXPIRED"));
    }
    
    @Test
    void leaseRenewalsAndALostLease() {
        InMemoryMachineLeaseStore store = new InMemoryMachineLeaseStore();
        LeaseStoreMachineIdDistributor distributor = new LeaseStoreMachineIdDistributor(store, MachineStateStorage.IN_MEMORY,
            ClockBackwardsSynchronizer.DEFAULT);
        InstanceId owner = InstanceId.of("owner", false);
        distributor.distribute("ns", 1, owner, Duration.ofMinutes(5));
        register();
        distributor.guard("ns", owner, Duration.ofMinutes(5));
        store.claimExpired("ns", InstanceId.of("thief", false), 1, Long.MAX_VALUE - 1, store.serverTime()).orElseThrow();
        assertThrows(RuntimeException.class, () -> distributor.guard("ns", owner, Duration.ofMinutes(5)));
        new MachineIdLease("ns", owner, Duration.ofMinutes(5)).markLost();
        assertThat(recording.events, contains("leaseRenewed", "leaseRenewFailed", "leaseLost"));
    }
    
    @Test
    void aCallTimeout() {
        register();
        assertThrows(CosIdException.class, () -> CallTimeout.of(Duration.ofMillis(20)).call("slow", () -> {
            Thread.sleep(2_000);
            return 1;
        }));
        assertThat(recording.events, contains("callTimeout"));
    }
    
    @Test
    void aRejectedCall() throws Exception {
        CallTimeout timeout = CallTimeout.of(Duration.ofSeconds(5));
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(CallTimeout.MAX_CONCURRENT_CALLS);
        ExecutorService callers = Executors.newFixedThreadPool(CallTimeout.MAX_CONCURRENT_CALLS);
        try {
            for (int i = 0; i < CallTimeout.MAX_CONCURRENT_CALLS; i++) {
                callers.execute(() -> timeout.call("busy", () -> {
                    started.countDown();
                    return release.await(5, TimeUnit.SECONDS);
                }));
            }
            assertThat(started.await(5, TimeUnit.SECONDS), equalTo(true));
            register();
            assertThrows(CosIdException.class, () -> timeout.call("one too many", () -> 1));
            assertThat(recording.events, contains("callRejected"));
        } finally {
            release.countDown();
            callers.shutdown();
        }
    }
}
