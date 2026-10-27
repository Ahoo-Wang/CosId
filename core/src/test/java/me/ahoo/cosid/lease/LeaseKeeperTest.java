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

package me.ahoo.cosid.lease;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.NamespaceMismatchException;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.store.tck.Fault;
import me.ahoo.cosid.store.tck.FaultInjector;
import me.ahoo.cosid.store.tck.FaultInjector.Operation;
import me.ahoo.cosid.store.tck.FaultySlotLeaseStore;
import me.ahoo.cosid.store.tck.InMemoryStore;
import me.ahoo.cosid.store.tck.ManualClock;
import me.ahoo.cosid.time.TimeSource;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link LeaseKeeper} on a manual store clock and manual elapsed time (roadmap R1-C): the renewal task is captured, and
 * each test runs its steps with {@link LeaseKeeper#runDue()}.
 */
class LeaseKeeperTest {
    private static final String NS = "app";
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final Duration TOLERANCE = Duration.ofSeconds(1);
    private static final long START = 1_700_000_000_000L;

    private final ManualClock clock = new ManualClock(START);
    private final InMemoryStore memory = new InMemoryStore(clock);
    private final FaultInjector faults = new FaultInjector();
    private final FaultySlotLeaseStore store = new FaultySlotLeaseStore(memory, faults);
    private final ManualTicker ticker = new ManualTicker(clock);
    private final List<Runnable> tasks = new ArrayList<>();
    private final RecordingListener listener = new RecordingListener();

    private LeaseKeeper keeper(SlotLeaseStore leaseStore, Owner owner, int bits, Duration acquireTimeout, Executor executor, TimeSource time) {
        return new LeaseKeeper(leaseStore, NS, owner, bits, TTL, TOLERANCE, OptionalInt.empty(), acquireTimeout, executor, time, listener, ticker);
    }

    private LeaseKeeper keeper(int bits) {
        return keeper(store, owner("host-a"), bits, TTL, tasks::add, clock);
    }

    private static Owner owner(String key) {
        return new Owner(key, UUID.randomUUID());
    }

    private SlotLease hold(String key, int bits, Duration ttl, OptionalInt preferred) {
        AcquireResult result = memory.acquire(NS, owner(key), 1 << bits, ttl, TOLERANCE, preferred);
        assertThat(result, instanceOf(AcquireResult.Acquired.class));
        return ((AcquireResult.Acquired) result).lease();
    }

    @Test
    void startAcquiresAndPublishesTheFirstBinding() {
        LeaseKeeper keeper = keeper(2);
        assertThrows(IllegalStateException.class, keeper::current);
        assertThat(keeper.lease(), is(Optional.empty()));
        keeper.start();
        SlotBinding binding = keeper.current();
        assertThat(binding.slot(), is(0));
        assertThat(binding.floor(), is(0L));
        assertThat(binding.ceiling(), is(START + TTL.toMillis() + TOLERANCE.toMillis()));
        assertThat(binding.generation(), is(1L));
        assertThat(binding.isValid(), is(true));
        assertThat(keeper.lease().orElseThrow().ceiling(), is(binding.ceiling()));
        assertThat(tasks.size(), is(1));
        assertThat(listener.events, contains("acquired:0:lowest"));
        assertThrows(IllegalStateException.class, keeper::start);
        keeper.close();
    }

    @Test
    void renewsEveryThirdOfTheTtlAndAdvancesTheWindow() {
        LeaseKeeper keeper = keeper(2);
        keeper.start();
        SlotBinding binding = keeper.current();
        assertThat(keeper.runDue(), is(TTL.toNanos() / 3));
        assertThat(faults.calls(Operation.RENEW), is(0L));
        for (int i = 1; i <= 5; i++) {
            ticker.advance(TTL.dividedBy(3));
            assertThat(keeper.runDue(), is(TTL.toNanos() / 3));
            assertThat(faults.calls(Operation.RENEW), is((long) i));
            assertThat(keeper.current(), sameInstance(binding));
            assertThat(binding.ceiling(), is(clock.currentMillis() + TTL.toMillis() + TOLERANCE.toMillis()));
            assertThat(binding.generation(), is(1L));
        }
        assertThat(listener.count("renewed:"), is(5L));
        keeper.close();
    }

    @Test
    void aLostRenewResponseIsReplayedWithoutFalseLoss() {
        LeaseKeeper keeper = keeper(2);
        keeper.start();
        SlotBinding binding = keeper.current();
        SlotLease before = keeper.lease().orElseThrow();
        faults.once(Operation.RENEW, new Fault.LoseResponse(() -> new CosIdException("timeout")));
        ticker.advance(TTL.dividedBy(3));
        assertThat("first retry after 100ms", keeper.runDue(), is(Duration.ofMillis(100).toNanos()));
        assertThat("the store applied the renewal", faults.calls(Operation.RENEW), is(1L));
        assertThat(binding.isValid(), is(true));
        assertThat("the client still knows the old ceiling only", binding.ceiling(), is(before.ceiling()));

        ticker.advance(Duration.ofMillis(100));
        keeper.runDue();
        assertThat(faults.calls(Operation.RENEW), is(2L));
        assertThat(keeper.current(), sameInstance(binding));
        assertThat(binding.isValid(), is(true));
        assertThat(binding.ceiling(), greaterThan(before.ceiling()));
        assertThat(keeper.lease().orElseThrow().fence(), is(before.fence()));
        assertThat(listener.events, contains("acquired:0:lowest", "renewFailed:0", "renewed:0"));
        keeper.close();
    }

    @Test
    void failedRenewalsBackOffUpToASixthOfTheTtl() {
        LeaseKeeper keeper = keeper(2);
        keeper.start();
        faults.always(Operation.RENEW, new Fault.Fail(() -> new CosIdException("down")));
        ticker.advance(TTL.dividedBy(3));
        List<Long> waits = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            long wait = keeper.runDue();
            waits.add(wait / 1_000_000);
            ticker.advance(Duration.ofNanos(wait));
        }
        assertThat(waits, contains(100L, 200L, 400L, 800L, 1600L, 3200L, 5000L, 5000L, 5000L));
        assertThat("the generators keep issuing up to the ceiling meanwhile", keeper.current().isValid(), is(true));
        faults.clear();
        keeper.runDue();
        assertThat(keeper.current().ceiling(), is(clock.currentMillis() + TTL.toMillis() + TOLERANCE.toMillis()));
        assertThat(keeper.runDue(), is(TTL.toNanos() / 3));
        keeper.close();
    }

    @Test
    void anEmptyRenewalInvalidatesTheBindingAndReacquiresWithANewGeneration() {
        LeaseKeeper keeper = keeper(2);
        keeper.start();
        SlotBinding first = keeper.current();
        // The keeper misses its renewals (a long pause); the lease expires and another owner takes the slot over.
        clock.advance(TTL.plusSeconds(1));
        SlotLease other = hold("host-b", 2, TTL, OptionalInt.of(first.slot()));
        assertThat(other.slot(), is(first.slot()));

        ticker.advance(TTL.dividedBy(3));
        keeper.runDue();
        assertThat(first.isValid(), is(false));
        SlotBinding second = keeper.current();
        assertThat(second, not(sameInstance(first)));
        assertThat(second.generation(), is(2L));
        assertThat(second.isValid(), is(true));
        assertThat(second.slot(), not(first.slot()));
        assertThat(listener.events, contains("acquired:0:lowest", "lost:0", "acquired:1:lowest"));
        keeper.close();
    }

    @Test
    void exhaustedAtRuntimeFailsFastAndKeepsRetrying() {
        LeaseKeeper keeper = keeper(1);
        keeper.start();
        SlotBinding first = keeper.current();
        hold("host-x", 1, Duration.ofMinutes(10), OptionalInt.empty());
        clock.advance(TTL.plusSeconds(1));
        SlotLease taker = hold("host-y", 1, Duration.ofMinutes(10), OptionalInt.empty());

        ticker.advance(TTL.dividedBy(3));
        long wait = keeper.runDue();
        assertThat(first.isValid(), is(false));
        assertThat("generators fail fast on the lost binding", keeper.current(), sameInstance(first));
        assertThat(keeper.lease(), is(Optional.empty()));
        assertThat("retries no later than the renew interval", wait, is(TTL.toNanos() / 3));

        ticker.advance(Duration.ofNanos(wait));
        keeper.runDue();
        assertThat(keeper.current(), sameInstance(first));

        assertThat(memory.release(taker, taker.floor()), is(true));
        ticker.advance(TTL.dividedBy(3));
        keeper.runDue();
        SlotBinding second = keeper.current();
        assertThat(second.isValid(), is(true));
        assertThat(second.generation(), is(2L));
        assertThat(second.slot(), is(taker.slot()));
        keeper.close();
    }

    @Test
    void startWaitsForAnExpiryWithinTheAcquireTimeout() {
        hold("host-x", 1, Duration.ofSeconds(5), OptionalInt.empty());
        hold("host-y", 1, Duration.ofSeconds(8), OptionalInt.empty());
        LeaseKeeper keeper = keeper(1);
        keeper.start();
        assertThat(keeper.current().isValid(), is(true));
        assertThat(clock.currentMillis(), greaterThan(START + 5_000));
        assertThat(clock.currentMillis(), lessThan(START + 8_000));
        keeper.close();
    }

    @Test
    void startFailsWhenNoSlotFreesWithinTheAcquireTimeout() {
        SlotLease x = hold("host-x", 1, Duration.ofMinutes(5), OptionalInt.empty());
        hold("host-y", 1, Duration.ofMinutes(5), OptionalInt.empty());
        Owner owner = owner("host-a");
        LeaseKeeper keeper = keeper(store, owner, 1, Duration.ofSeconds(20), tasks::add, clock);
        CosIdException error = assertThrows(CosIdException.class, keeper::start);
        assertThat(error.getMessage(), containsString("every slot is held"));
        assertThat(clock.currentMillis(), greaterThanOrEqualTo(START + 20_000));
        assertThrows(IllegalStateException.class, keeper::current);
        assertThat(tasks.isEmpty(), is(true));
        // The failed keeper gave its incarnation back.
        assertThat(memory.release(x, x.floor()), is(true));
        LeaseKeeper again = keeper(memory, owner, 1, TTL, tasks::add, clock);
        again.start();
        again.close();
    }

    @Test
    void startRetriesStoreErrorsAndPropagatesANamespaceMismatch() {
        faults.once(Operation.ACQUIRE, new Fault.Fail(() -> new CosIdException("down")));
        LeaseKeeper keeper = keeper(2);
        keeper.start();
        assertThat(faults.calls(Operation.ACQUIRE), is(1L));
        assertThat(keeper.current().isValid(), is(true));
        keeper.close();

        faults.always(Operation.ACQUIRE, new Fault.Fail(() -> new CosIdException("down")));
        CosIdException down = assertThrows(CosIdException.class, () -> keeper(store, owner("host-a"), 2, Duration.ofSeconds(1), tasks::add, clock).start());
        assertThat(down.getMessage(), containsString("the store failed"));
        faults.clear();

        assertThrows(NamespaceMismatchException.class, () -> keeper(4).start());
    }

    @Test
    void closeReleasesWithTheExactLastIssuedAfterInvalidatingTheBinding() {
        AtomicReference<LeaseKeeper> self = new AtomicReference<>();
        List<Boolean> validAtRelease = new ArrayList<>();
        SlotLeaseStore observing = new SlotLeaseStore() {
            @Override
            public AcquireResult acquire(String namespace, Owner owner, int limit, Duration ttl, Duration tolerance, OptionalInt preferredSlot) {
                return store.acquire(namespace, owner, limit, ttl, tolerance, preferredSlot);
            }

            @Override
            public Optional<SlotLease> renew(SlotLease lease, Duration ttl, Duration tolerance) {
                return store.renew(lease, ttl, tolerance);
            }

            @Override
            public boolean release(SlotLease lease, long lastIssued) {
                validAtRelease.add(self.get().current().isValid());
                return store.release(lease, lastIssued);
            }
        };
        LeaseKeeper keeper = keeper(observing, owner("host-a"), 1, TTL, tasks::add, clock);
        self.set(keeper);
        keeper.start();
        SlotBinding binding = keeper.current();
        long lastIssued = START + 1_234;
        keeper.close(lastIssued);
        assertThat("generators are stopped before the release reaches the store (§4.3)", validAtRelease, contains(false));
        assertThat(binding.isValid(), is(false));
        assertThat(keeper.lease(), is(Optional.empty()));
        assertThat(keeper.runDue(), is(-1L));
        keeper.close(lastIssued);
        keeper.close();
        assertThat(faults.calls(Operation.RELEASE), is(1L));
        assertThat(listener.events, hasItem("released:0"));

        SlotLease next = hold("host-b", 1, TTL, OptionalInt.empty());
        assertThat(next.slot(), is(binding.slot()));
        assertThat("the next holder starts right after the last issued timestamp", next.floor(), is(lastIssued));
    }

    @Test
    void closeWithoutLastIssuedReleasesUpToTheStoredCeiling() {
        LeaseKeeper keeper = keeper(1);
        keeper.start();
        faults.once(Operation.RENEW, new Fault.LoseResponse(() -> new CosIdException("timeout")));
        ticker.advance(TTL.dividedBy(3));
        keeper.runDue();
        long knownCeiling = keeper.current().ceiling();
        keeper.close();
        SlotLease next = hold("host-b", 1, TTL, OptionalInt.empty());
        assertThat("the renewal the client never saw still bounds the next floor", next.floor(), greaterThan(knownCeiling));
    }

    @Test
    void aFailedReleaseIsRetriedOnceAndTheLeaseExpiresOtherwise() {
        LeaseKeeper keeper = keeper(1);
        keeper.start();
        faults.always(Operation.RELEASE, new Fault.Fail(() -> new CosIdException("down")));
        keeper.close(START);
        assertThat(listener.count("released:"), is(0L));
        faults.clear();

        LeaseKeeper lost = keeper(1);
        lost.start();
        faults.once(Operation.RELEASE, new Fault.LoseResponse(() -> new CosIdException("timeout")));
        lost.close(START);
        assertThat("the replay finds the lease released", faults.calls(Operation.RELEASE), is(2L));
    }

    @Test
    void twoKeepersWithTheSameOwnerKeyNeverShareASlot() {
        LeaseKeeper first = keeper(store, owner("host-a"), 2, TTL, tasks::add, clock);
        LeaseKeeper second = keeper(store, owner("host-a"), 2, TTL, tasks::add, clock);
        first.start();
        assertThat(listener.count("duplicate:"), is(0L));
        second.start();
        assertThat(first.current().slot(), not(second.current().slot()));
        assertThat("the store reports the other live incarnation of the key", listener.events, contains("acquired:0:lowest", "acquired:1:lowest",
            "duplicate:host-a"));
        assertThat(first.owner().incarnation(), not(second.owner().incarnation()));
        first.close();
        second.close();
    }

    @Test
    void keepersCannotShareAnIncarnation() {
        Owner owner = owner("host-a");
        LeaseKeeper first = keeper(store, owner, 2, TTL, tasks::add, clock);
        first.start();
        LeaseKeeper second = keeper(store, owner, 2, TTL, tasks::add, clock);
        IllegalStateException error = assertThrows(IllegalStateException.class, second::start);
        assertThat(error.getMessage(), containsString("needs its own"));
        first.close();
        second.close();
        LeaseKeeper third = keeper(store, owner, 2, TTL, tasks::add, clock);
        third.start();
        third.close();
    }

    @Test
    void reportsClockSkewBeyondHalfTheTolerance() {
        TimeSource ahead = () -> clock.currentMillis() + 2_000;
        LeaseKeeper keeper = keeper(store, owner("host-a"), 2, TTL, tasks::add, ahead);
        keeper.start();
        assertThat(listener.events, hasItem("skew:-2000"));
        keeper.close();

        listener.events.clear();
        TimeSource close = () -> clock.currentMillis() + 400;
        LeaseKeeper fine = keeper(store, owner("host-b"), 2, TTL, tasks::add, close);
        fine.start();
        assertThat(listener.events, everyItem(not(startsWith("skew:"))));
        fine.close();
    }

    @Test
    void reportsTheReasonTheStoreGives() {
        LeaseKeeper keeper = new LeaseKeeper(store, NS, owner("host-a"), 2, TTL, TOLERANCE, OptionalInt.of(2), TTL, tasks::add, clock, listener, ticker);
        keeper.start();
        assertThat(keeper.current().slot(), is(2));
        assertThat(listener.events, contains("acquired:2:preferred"));
        keeper.close();

        LeaseKeeper sticky = keeper(2);
        sticky.start();
        assertThat("host-a held slot 2 last", sticky.current().slot(), is(2));
        assertThat(listener.events, hasItem("acquired:2:sticky"));
        sticky.close();
    }

    @Test
    void rejectsAnExecutorThatRunsOnTheCallingThreadAndReleasesTheSlot() {
        LeaseKeeper keeper = keeper(store, owner("host-a"), 1, TTL, Runnable::run, clock);
        assertThrows(IllegalArgumentException.class, keeper::start);
        assertThat(listener.events, hasItem("released:0"));

        LeaseKeeper rejected = keeper(store, owner("host-a"), 1, TTL, task -> {
            throw new RejectedExecutionException("shut down");
        }, clock);
        assertThrows(CosIdException.class, rejected::start);
        assertThat(listener.count("released:"), is(2L));
    }

    @Test
    void closingWhileStartingStopsTheStart() {
        AtomicReference<LeaseKeeper> self = new AtomicReference<>();
        SlotLeaseStore closingOnAcquire = new SlotLeaseStore() {
            @Override
            public AcquireResult acquire(String namespace, Owner owner, int limit, Duration ttl, Duration tolerance, OptionalInt preferredSlot) {
                AcquireResult result = store.acquire(namespace, owner, limit, ttl, tolerance, preferredSlot);
                self.get().close();
                return result;
            }

            @Override
            public Optional<SlotLease> renew(SlotLease lease, Duration ttl, Duration tolerance) {
                return store.renew(lease, ttl, tolerance);
            }

            @Override
            public boolean release(SlotLease lease, long lastIssued) {
                return store.release(lease, lastIssued);
            }
        };
        LeaseKeeper acquiring = keeper(closingOnAcquire, owner("host-a"), 1, TTL, tasks::add, clock);
        self.set(acquiring);
        assertThrows(IllegalStateException.class, acquiring::start);
        assertThat("the slot acquired meanwhile is released", listener.events, hasItem("released:0"));

        hold("host-x", 1, TTL, OptionalInt.empty());
        hold("host-y", 1, TTL, OptionalInt.empty());
        LeaseKeeper waiting = keeper(closingOnAcquire, owner("host-a"), 1, TTL, tasks::add, clock);
        self.set(waiting);
        assertThrows(IllegalStateException.class, waiting::start);
    }

    @Test
    void aFailingListenerDoesNotStopTheKeeper() {
        GeneratorListener failing = new GeneratorListener() {
            @Override
            public void onSlotRenewed(SlotLease lease) {
                throw new IllegalStateException("listener bug");
            }
        };
        LeaseKeeper keeper = new LeaseKeeper(store, NS, owner("host-a"), 2, TTL, TOLERANCE, OptionalInt.empty(), TTL, tasks::add, clock, failing, ticker);
        keeper.start();
        ticker.advance(TTL.dividedBy(3));
        assertThat(keeper.runDue(), is(TTL.toNanos() / 3));
        keeper.close();
    }

    @Test
    void validatesItsConfiguration() {
        Owner owner = owner("host-a");
        assertThrows(IllegalArgumentException.class, () -> new LeaseKeeper(store, NS, owner, 2, Duration.ZERO, TOLERANCE, OptionalInt.empty(), TTL,
            tasks::add, clock, listener));
        assertThrows(IllegalArgumentException.class, () -> new LeaseKeeper(store, NS, owner, 2, TTL, Duration.ofSeconds(-1), OptionalInt.empty(), TTL,
            tasks::add, clock, listener));
        assertThrows(IllegalArgumentException.class, () -> new LeaseKeeper(store, NS, owner, 2, TTL, TOLERANCE, OptionalInt.of(4), TTL,
            tasks::add, clock, listener));
        assertThrows(IllegalArgumentException.class, () -> new LeaseKeeper(store, NS, owner, 2, TTL, TOLERANCE, OptionalInt.empty(), Duration.ofSeconds(-1),
            tasks::add, clock, listener));
    }

    @Test
    void renewsOnARealExecutorAndStopsOnClose() throws InterruptedException {
        InMemoryStore real = new InMemoryStore();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            LeaseKeeper keeper = new LeaseKeeper(real, NS, owner("host-a"), 2, Duration.ofMillis(300), Duration.ofMillis(100), OptionalInt.empty(),
                Duration.ofSeconds(5), executor, TimeSource.SYSTEM, listener);
            keeper.start();
            long firstCeiling = keeper.current().ceiling();
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (listener.count("renewed:") < 3 && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertThat(listener.count("renewed:"), greaterThanOrEqualTo(3L));
            assertThat(keeper.current().ceiling(), greaterThan(firstCeiling));
            assertThat(keeper.current().generation(), is(1L));
            keeper.close();
            assertThat(listener.count("released:"), is(1L));
        } finally {
            executor.shutdownNow();
        }
    }
}
