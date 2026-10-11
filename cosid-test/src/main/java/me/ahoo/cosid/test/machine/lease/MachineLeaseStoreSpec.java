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

package me.ahoo.cosid.test.machine.lease;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.lease.Lease;
import me.ahoo.cosid.machine.lease.MachineLeaseStore;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Test;

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

/**
 * Compatibility kit of {@link MachineLeaseStore}: every implementation must pass it unchanged.
 */
public abstract class MachineLeaseStoreSpec {
    private static final int CONCURRENCY = 16;
    private static final long T0 = 1_000_000L;
    private static final int MAX_MACHINE_ID = 1023;
    
    protected abstract MachineLeaseStore getStore();
    
    protected static String newNamespace(String prefix) {
        return MockIdGenerator.usePrefix(prefix).generateAsString();
    }
    
    private static InstanceId instance(String name) {
        return InstanceId.of(name, false);
    }
    
    private static InstanceId stableInstance(String name) {
        return InstanceId.of(name, true);
    }
    
    @Test
    public void claimNewTakesSequentialMachineIdsUntilFull() {
        MachineLeaseStore store = getStore();
        String namespace = newNamespace("claimNew");
        for (int machineId = 0; machineId <= 3; machineId++) {
            Lease lease = store.claimNew(namespace, instance("i" + machineId), 3, T0).orElseThrow();
            assertThat(lease.getMachineId(), equalTo(machineId));
            assertThat(lease.getNamespace(), equalTo(namespace));
            assertThat(lease.getLastTimestamp(), greaterThanOrEqualTo(T0));
        }
        assertThat(store.claimNew(namespace, instance("overflow"), 3, T0).isPresent(), equalTo(false));
    }
    
    @Test
    public void claimSelfTakesBackOwnLeaseRenewed() {
        MachineLeaseStore store = getStore();
        String namespace = newNamespace("claimSelf");
        Lease claimed = store.claimNew(namespace, instance("owner"), 3, T0).orElseThrow();
        
        Lease self = store.claimSelf(namespace, instance("owner"), T0 + 10).orElseThrow();
        
        assertThat(self.getMachineId(), equalTo(claimed.getMachineId()));
        assertThat(self.getLastTimestamp(), greaterThanOrEqualTo(T0 + 10));
        assertThat(self.getVersion(), not(equalTo(claimed.getVersion())));
        assertThat(store.claimSelf(namespace, instance("stranger"), T0).isPresent(), equalTo(false));
    }
    
    @Test
    public void renewRequiresTheCurrentVersion() {
        MachineLeaseStore store = getStore();
        String namespace = newNamespace("renew");
        Lease claimed = store.claimNew(namespace, instance("owner"), 3, T0).orElseThrow();
        
        Lease renewed = store.renew(claimed, T0 + 10).orElseThrow();
        
        assertThat(renewed.getLastTimestamp(), greaterThanOrEqualTo(T0 + 10));
        assertThat(store.renew(claimed, T0 + 20).isPresent(), equalTo(false));
        assertThat(store.renew(renewed, T0 + 20).isPresent(), equalTo(true));
    }
    
    @Test
    public void ownerFindsOutWhenItsLeaseIsStolenBetweenRenewals() {
        MachineLeaseStore store = getStore();
        String namespace = newNamespace("stolen");
        Lease owner = store.claimNew(namespace, instance("owner"), 0, T0).orElseThrow();
        
        Lease thief = store.claimExpired(namespace, instance("thief"), MAX_MACHINE_ID, T0 + 100, T0 + 200).orElseThrow();
        
        assertThat(thief.getMachineId(), equalTo(owner.getMachineId()));
        assertThat(store.renew(owner, T0 + 300).isPresent(), equalTo(false));
        assertThat(store.release(owner, T0 + 300), equalTo(false));
        assertThat(store.claimSelf(namespace, instance("owner"), T0 + 300).isPresent(), equalTo(false));
        assertThat(store.renew(thief, T0 + 300).isPresent(), equalTo(true));
    }
    
    @Test
    public void claimExpiredSkipsFreshAndStableLeases() {
        MachineLeaseStore store = getStore();
        String namespace = newNamespace("expired");
        final Lease stable = store.claimNew(namespace, stableInstance("stable"), 3, T0).orElseThrow();
        Lease expired = store.claimNew(namespace, instance("expired"), 3, T0).orElseThrow();
        store.claimNew(namespace, instance("fresh"), 3, T0 + 1000).orElseThrow();
        
        Lease claimed = store.claimExpired(namespace, instance("newcomer"), MAX_MACHINE_ID, T0 + 10, T0 + 2000).orElseThrow();
        
        assertThat(claimed.getMachineId(), equalTo(expired.getMachineId()));
        assertThat(claimed.getLastTimestamp(), greaterThanOrEqualTo(T0 + 2000));
        assertThat(store.claimExpired(namespace, instance("another"), MAX_MACHINE_ID, T0 + 10, T0 + 2000).isPresent(), equalTo(false));
        // Long past the stable owner's last renewal: still never recycled.
        assertThat(store.claimExpired(namespace, instance("another"), MAX_MACHINE_ID, Long.MAX_VALUE - 1, T0 + 2000).map(Lease::getMachineId),
            not(equalTo(Optional.of(stable.getMachineId()))));
    }
    
    @Test
    public void releasedMachineIdGoesToTheNextClaimer() {
        MachineLeaseStore store = getStore();
        String namespace = newNamespace("released");
        Lease owner = store.claimNew(namespace, instance("owner"), 3, T0).orElseThrow();
        
        assertThat(store.release(owner, T0 + 10), equalTo(true));
        
        assertThat(store.claimSelf(namespace, instance("owner"), T0 + 20).isPresent(), equalTo(false));
        Lease next = store.claimReleased(namespace, instance("next"), MAX_MACHINE_ID, T0 + 20).orElseThrow();
        assertThat(next.getMachineId(), equalTo(owner.getMachineId()));
        assertThat(store.claimReleased(namespace, instance("late"), MAX_MACHINE_ID, T0 + 20).isPresent(), equalTo(false));
    }
    
    @Test
    public void stableInstanceKeepsItsMachineIdAfterRelease() {
        MachineLeaseStore store = getStore();
        String namespace = newNamespace("stableRelease");
        Lease owner = store.claimNew(namespace, stableInstance("stable"), 3, T0).orElseThrow();
        
        assertThat(store.release(owner, T0 + 10), equalTo(true));
        
        assertThat(store.claimReleased(namespace, instance("other"), MAX_MACHINE_ID, T0 + 20).isPresent(), equalTo(false));
        assertThat(store.claimExpired(namespace, instance("other"), MAX_MACHINE_ID, Long.MAX_VALUE - 1, T0 + 20).isPresent(), equalTo(false));
        assertThat(store.claimSelf(namespace, stableInstance("stable"), T0 + 20).orElseThrow().getMachineId(), equalTo(owner.getMachineId()));
    }
    
    @Test
    public void lastTimestampNeverMovesBackwards() {
        MachineLeaseStore store = getStore();
        String namespace = newNamespace("monotonic");
        Lease owner = store.claimNew(namespace, instance("owner"), 3, T0).orElseThrow();
        // Ids were generated up to T0 + 5000 before the shutdown; the next owner starts with a clock behind that.
        store.release(owner, T0 + 5000);
        
        Lease next = store.claimReleased(namespace, instance("next"), MAX_MACHINE_ID, T0 + 2000).orElseThrow();
        
        assertThat(next.getLastTimestamp(), greaterThanOrEqualTo(T0 + 5000));
        assertThat(store.renew(next, T0 + 3000).orElseThrow().getLastTimestamp(), greaterThanOrEqualTo(T0 + 5000));
    }
    
    @Test
    public void concurrentClaimNewHandsOutDistinctMachineIds() throws Exception {
        MachineLeaseStore store = getStore();
        String namespace = newNamespace("concurrentNew");
        int maxMachineId = CONCURRENCY - 1;
        List<Optional<Lease>> results = concurrently(CONCURRENCY + 4,
            index -> () -> store.claimNew(namespace, instance("i" + index), maxMachineId, T0));
        
        Set<Integer> machineIds = new HashSet<>();
        int claimed = 0;
        for (Optional<Lease> result : results) {
            if (result.isPresent()) {
                claimed++;
                machineIds.add(result.get().getMachineId());
            }
        }
        assertThat(claimed, equalTo(CONCURRENCY));
        assertThat(machineIds, hasSize(CONCURRENCY));
    }
    
    @Test
    public void concurrentClaimExpiredHasOneWinner() throws Exception {
        MachineLeaseStore store = getStore();
        String namespace = newNamespace("concurrentExpired");
        store.claimNew(namespace, instance("owner"), 0, T0).orElseThrow();
        
        List<Optional<Lease>> results = concurrently(CONCURRENCY,
            index -> () -> store.claimExpired(namespace, instance("i" + index), MAX_MACHINE_ID, T0 + 100, T0 + 200));
        
        assertThat(results.stream().filter(Optional::isPresent).count(), equalTo(1L));
    }
    
    @Test
    public void concurrentClaimReleasedHasOneWinner() throws Exception {
        MachineLeaseStore store = getStore();
        String namespace = newNamespace("concurrentReleased");
        store.release(store.claimNew(namespace, instance("owner"), 0, T0).orElseThrow(), T0 + 10);
        
        List<Optional<Lease>> results = concurrently(CONCURRENCY,
            index -> () -> store.claimReleased(namespace, instance("i" + index), MAX_MACHINE_ID, T0 + 20));
        
        assertThat(results.stream().filter(Optional::isPresent).count(), equalTo(1L));
    }
    
    @Test
    public void concurrentRenewOfOneVersionHasOneWinner() throws Exception {
        MachineLeaseStore store = getStore();
        String namespace = newNamespace("concurrentRenew");
        Lease lease = store.claimNew(namespace, instance("owner"), 0, T0).orElseThrow();
        
        List<Optional<Lease>> results = concurrently(CONCURRENCY, index -> () -> store.renew(lease, T0 + 10 + index));
        
        assertThat(results.stream().filter(Optional::isPresent).count(), equalTo(1L));
    }
    
    @Test
    public void leaseExpiresExactlyAtExpiredBefore() {
        MachineLeaseStore store = getStore();
        String namespace = newNamespace("boundary");
        Lease owner = store.claimNew(namespace, instance("owner"), 0, T0).orElseThrow();
        
        assertThat(store.claimExpired(namespace, instance("early"), MAX_MACHINE_ID, owner.getLastTimestamp() - 1, T0 + 100).isPresent(),
            equalTo(false));
        assertThat(store.claimExpired(namespace, instance("onTime"), MAX_MACHINE_ID, owner.getLastTimestamp(), T0 + 100).isPresent(),
            equalTo(true));
    }
    
    @Test
    public void claimsRespectMaxMachineId() {
        MachineLeaseStore store = getStore();
        String namespace = newNamespace("maxMachineId");
        for (int machineId = 0; machineId < 3; machineId++) {
            store.claimNew(namespace, instance("live" + machineId), 3, T0 + 10_000).orElseThrow();
        }
        Lease high = store.claimNew(namespace, instance("high"), 3, T0).orElseThrow();
        assertThat(high.getMachineId(), equalTo(3));
        
        // machineBit lowered: id 3 is out of range now.
        assertThat(store.claimExpired(namespace, instance("expiredClaimer"), 2, T0 + 100, T0 + 200).isPresent(), equalTo(false));
        store.release(high, T0 + 10);
        assertThat(store.claimReleased(namespace, instance("releasedClaimer"), 2, T0 + 200).isPresent(), equalTo(false));
        assertThat(store.claimReleased(namespace, instance("releasedClaimer"), 3, T0 + 200).orElseThrow().getMachineId(), equalTo(3));
    }
    
    @Test
    public void stableFlagChangeIsPersisted() {
        MachineLeaseStore store = getStore();
        String namespace = newNamespace("stableChange");
        Lease temporary = store.claimNew(namespace, instance("node"), 3, T0).orElseThrow();
        
        // The same instance id restarts as stable: its lease must no longer be recycled.
        Lease stable = store.claimSelf(namespace, stableInstance("node"), T0 + 10).orElseThrow();
        assertThat(stable.getMachineId(), equalTo(temporary.getMachineId()));
        assertThat(stable.isStable(), equalTo(true));
        assertThat(store.claimExpired(namespace, instance("other"), MAX_MACHINE_ID, Long.MAX_VALUE - 1, T0 + 20).isPresent(), equalTo(false));
        
        // And back to temporary: released on shutdown again.
        Lease backToTemporary = store.claimSelf(namespace, instance("node"), T0 + 30).orElseThrow();
        assertThat(backToTemporary.isStable(), equalTo(false));
        assertThat(store.release(backToTemporary, T0 + 40), equalTo(true));
        assertThat(store.claimReleased(namespace, instance("other"), MAX_MACHINE_ID, T0 + 50).isPresent(), equalTo(true));
    }
    
    @Test
    public void renewOfReleasedLeaseFails() {
        MachineLeaseStore store = getStore();
        String namespace = newNamespace("renewReleased");
        Lease lease = store.claimNew(namespace, instance("owner"), 3, T0).orElseThrow();
        
        assertThat(store.release(lease, T0 + 10), equalTo(true));
        
        assertThat(store.renew(lease, T0 + 20).isPresent(), equalTo(false));
        assertThat(store.release(lease, T0 + 20), equalTo(false));
    }
    
    @Test
    public void namespacesAreIsolated() {
        MachineLeaseStore store = getStore();
        String first = newNamespace("isolatedA");
        String second = newNamespace("isolatedB");
        Lease inFirst = store.claimNew(first, instance("owner"), 3, T0).orElseThrow();
        
        assertThat(store.claimNew(second, instance("owner"), 3, T0).orElseThrow().getMachineId(), equalTo(inFirst.getMachineId()));
        assertThat(store.claimExpired(second, instance("thief"), MAX_MACHINE_ID, Long.MAX_VALUE - 1, T0 + 10).map(Lease::getNamespace),
            not(equalTo(Optional.of(first))));
        store.release(inFirst, T0 + 10);
        assertThat(store.claimReleased(second, instance("other"), MAX_MACHINE_ID, T0 + 20).isPresent(), equalTo(false));
        assertThat(store.claimSelf(second, instance("stranger"), T0 + 20).isPresent(), equalTo(false));
    }
    
    @Test
    public void everyWriteStrictlyIncreasesTheLastTimestamp() {
        MachineLeaseStore store = getStore();
        String namespace = newNamespace("strict");
        Lease claimed = store.claimNew(namespace, instance("owner"), 3, T0).orElseThrow();
        // Same and earlier "now": the timestamp, and so the version, must still change.
        Lease renewedSameTime = store.renew(claimed, T0).orElseThrow();
        Lease renewedEarlier = store.renew(renewedSameTime, T0 - 100).orElseThrow();
        Lease self = store.claimSelf(namespace, instance("owner"), T0 - 200).orElseThrow();
        
        assertThat(renewedSameTime.getLastTimestamp(), greaterThan(claimed.getLastTimestamp()));
        assertThat(renewedEarlier.getLastTimestamp(), greaterThan(renewedSameTime.getLastTimestamp()));
        assertThat(self.getLastTimestamp(), greaterThan(renewedEarlier.getLastTimestamp()));
        Set<Long> versions = Set.of(claimed.getVersion(), renewedSameTime.getVersion(), renewedEarlier.getVersion(), self.getVersion());
        assertThat(versions, hasSize(4));
        assertThat(store.renew(renewedSameTime, T0 + 10).isPresent(), equalTo(false));
    }
    
    @Test
    public void claimSelfRacingClaimExpiredHasOneWinner() throws Exception {
        for (int round = 0; round < 20; round++) {
            MachineLeaseStore store = getStore();
            String namespace = newNamespace("selfVsExpired");
            store.claimNew(namespace, instance("owner"), 0, T0).orElseThrow();
            
            List<Optional<Lease>> results = concurrently(CONCURRENCY, index -> index == 0
                ? () -> store.claimSelf(namespace, instance("owner"), T0 + 200)
                : () -> store.claimExpired(namespace, instance("thief" + index), MAX_MACHINE_ID, T0 + 100, T0 + 200));
            
            assertThat(results.stream().filter(Optional::isPresent).count(), equalTo(1L));
        }
    }
    
    @Test
    public void serverTimeIsWallClockTime() {
        assertThat(getStore().serverTime(), greaterThan(0L));
    }
    
    @FunctionalInterface
    private interface IndexedTask<T> {
        Callable<T> create(int index);
    }
    
    private static <T> List<T> concurrently(int threads, IndexedTask<T> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<T> task = tasks.create(i);
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
