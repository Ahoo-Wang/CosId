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

package me.ahoo.cosid.store.tck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import me.ahoo.cosid.NamespaceMismatchException;
import me.ahoo.cosid.lease.AcquireReason;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.lease.SlotLeaseStore;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * The {@link SlotLeaseStore} contract (05-machine-lease.md §2, 06-stores.md §3). A backend test extends it and
 * implements {@link #newStore()}. Every case uses a namespace of its own, so the cases share one backend safely.
 *
 * <p>The cases never read a client clock. The ones that need a lease to expire acquire it with {@link #shortTtl()}
 * and retry the takeover until the store lets it through, so they hold on a loaded machine; a store whose clock the
 * test controls returns it from {@link #storeClock()}, and the cases move it instead of waiting.
 */
public abstract class SlotLeaseStoreContract {
    /** The tolerance of every namespace of the contract (D20 default). */
    protected static final Duration TOLERANCE = Duration.ofSeconds(1);
    /** The ttl of leases that must not expire during a case. */
    protected static final Duration LONG_TTL = Duration.ofSeconds(60);

    /**
     * A store on the backend under test. Each call opens a new instance on the same data, so tests can act as several
     * clients; tests close what they open.
     *
     * @return a new store instance
     */
    protected abstract SlotLeaseStore newStore();

    /**
     * The ttl of tests that wait for a lease to expire: short, but long enough for the clock resolution of the backend.
     *
     * @return the ttl
     */
    protected Duration shortTtl() {
        return Duration.ofMillis(500);
    }

    /**
     * The store clock, when the test controls it: the expiry cases then move it past the expiry instead of waiting.
     * A real backend keeps the default.
     *
     * @return the controllable store clock, empty by default
     */
    protected Optional<ManualClock> storeClock() {
        return Optional.empty();
    }

    /**
     * How long a case waits for an expired lease to be taken over before it fails.
     *
     * @return the wait, by default 20 short ttls plus 10 seconds
     */
    protected Duration takeoverTimeout() {
        return shortTtl().multipliedBy(20).plusSeconds(10);
    }

    /**
     * How far the store clock may be from the clock of the test machine, for the plausibility check of
     * {@link #storeNowComesFromTheStore()} on a store without a controllable clock.
     *
     * @return the bound, 5 minutes by default
     */
    protected Duration maxStoreClockOffset() {
        return Duration.ofMinutes(5);
    }

    /**
     * How many clients {@link #concurrentAcquiresGetDistinctSlots()} runs at once; it uses {@code clients * 2 / 3}
     * slots, so a third of them find the namespace exhausted.
     *
     * @return the client count, 24 by default
     */
    protected int concurrentClients() {
        return 24;
    }

    /**
     * How many store instances the concurrency cases spread the clients over.
     *
     * @return the instance count, 4 by default
     */
    protected int storeInstances() {
        return 4;
    }

    private static String newNamespace() {
        return "tck-" + UUID.randomUUID();
    }

    private static Owner owner(String key) {
        return new Owner(key, UUID.randomUUID());
    }

    private static AcquireResult.Acquired acquiredResult(SlotLeaseStore store, String namespace, Owner owner, int limit, Duration ttl,
                                                         OptionalInt preferredSlot) {
        AcquireResult result = store.acquire(namespace, owner, limit, ttl, TOLERANCE, preferredSlot);
        return assertInstanceOf(AcquireResult.Acquired.class, result, () -> owner + " acquires in " + namespace);
    }

    private static SlotLease acquired(SlotLeaseStore store, String namespace, Owner owner, int limit, Duration ttl, OptionalInt preferredSlot) {
        return acquiredResult(store, namespace, owner, limit, ttl, preferredSlot).lease();
    }

    private static SlotLease acquired(SlotLeaseStore store, String namespace, Owner owner, int limit) {
        return acquired(store, namespace, owner, limit, LONG_TTL, OptionalInt.empty());
    }

    /** Acquires and checks the reason the store reports, and that no other incarnation of the key is live. */
    private static SlotLease acquiredFor(AcquireReason reason, SlotLeaseStore store, String namespace, Owner owner, int limit, OptionalInt preferredSlot) {
        AcquireResult.Acquired result = acquiredResult(store, namespace, owner, limit, LONG_TTL, preferredSlot);
        assertEquals(reason, result.reason(), () -> "reason of " + result);
        assertFalse(result.duplicateOwnerKey(), () -> "no other incarnation of " + owner.key() + " is live: " + result);
        return result.lease();
    }

    private static SlotLease renewed(SlotLeaseStore store, SlotLease lease, Duration ttl) {
        return store.renew(lease, ttl, TOLERANCE).orElseThrow(() -> new AssertionError("renew of " + lease + " came back empty"));
    }

    /**
     * Acquires once the store lets {@code owner} in, after some lease expired: moves the store clock past the earliest
     * expiry when the test controls it, otherwise retries until {@link #takeoverTimeout()}.
     */
    private SlotLease awaitAcquired(SlotLeaseStore store, String namespace, Owner owner, int limit, Duration ttl) {
        return awaitAcquiredResult(store, namespace, owner, limit, ttl).lease();
    }

    private AcquireResult.Acquired awaitAcquiredResult(SlotLeaseStore store, String namespace, Owner owner, int limit, Duration ttl) {
        long deadline = System.nanoTime() + takeoverTimeout().toNanos();
        while (true) {
            AcquireResult result = store.acquire(namespace, owner, limit, ttl, TOLERANCE, OptionalInt.empty());
            if (result instanceof AcquireResult.Acquired acquired) {
                return acquired;
            }
            long earliestExpiresAt = ((AcquireResult.Exhausted) result).earliestExpiresAt();
            Optional<ManualClock> clock = storeClock();
            if (clock.isPresent()) {
                clock.get().set(Math.max(clock.get().currentMillis(), earliestExpiresAt + 1));
                continue;
            }
            if (System.nanoTime() > deadline) {
                fail(owner + " could not take over in " + namespace + " within " + takeoverTimeout() + "; earliest expiry " + earliestExpiresAt);
            }
            try {
                Thread.sleep(Math.max(10, shortTtl().toMillis() / 10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted", e);
            }
        }
    }

    /** Basics: acquiring again with the same key and incarnation returns the same slot and fence. */
    @Test
    public void acquireIsIdempotentForTheSameOwner() {
        String namespace = newNamespace();
        Owner owner = owner("host-a");
        try (SlotLeaseStore store = newStore(); SlotLeaseStore other = newStore()) {
            acquired(store, namespace, owner("host-0"), 8);
            SlotLease first = acquiredFor(AcquireReason.LOWEST, store, namespace, owner, 8, OptionalInt.empty());
            SlotLease again = acquiredFor(AcquireReason.IDEMPOTENT, other, namespace, owner, 8, OptionalInt.of(5));
            assertEquals(first.slot(), again.slot(), "the held slot wins over the preferred one");
            assertEquals(first.fence(), again.fence());
            assertEquals(first.owner(), again.owner());
            assertEquals(first.floor(), again.floor());
            assertTrue(store.release(again, again.ceiling()), "the lease returned by the retry is the live one");
        }
    }

    /** Basics: a free preferred slot is taken before the sticky and the lowest ones. */
    @Test
    public void acquireTakesThePreferredSlot() {
        String namespace = newNamespace();
        try (SlotLeaseStore store = newStore()) {
            SlotLease x = acquired(store, namespace, owner("host-x"), 8);
            SlotLease k = acquired(store, namespace, owner("host-k"), 8);
            assertTrue(store.release(x, x.ceiling()));
            assertTrue(store.release(k, k.ceiling()));
            // Now slot x.slot() is the lowest free slot and k.slot() the sticky one of host-k.
            SlotLease preferred = acquiredFor(AcquireReason.PREFERRED, store, namespace, owner("host-k"), 8, OptionalInt.of(5));
            assertEquals(5, preferred.slot());
            SlotLease notPreferred = acquiredFor(AcquireReason.LOWEST, store, namespace, owner("host-z"), 8, OptionalInt.of(5));
            assertNotEquals(5, notPreferred.slot(), "a held preferred slot is passed over");
            assertEquals(x.slot(), notPreferred.slot(), "then the lowest free slot is taken");
        }
    }

    /** Basics: a new incarnation of an owner key gets back the free slot that key held last. */
    @Test
    public void acquireTakesTheStickySlotOfTheOwnerKey() {
        String namespace = newNamespace();
        try (SlotLeaseStore store = newStore()) {
            SlotLease x = acquired(store, namespace, owner("host-x"), 8);
            SlotLease k = acquired(store, namespace, owner("host-k"), 8);
            assertNotEquals(x.slot(), k.slot());
            assertTrue(store.release(x, x.ceiling()));
            assertTrue(store.release(k, k.ceiling()));
            SlotLease sticky = acquiredFor(AcquireReason.STICKY, store, namespace, owner("host-k"), 8, OptionalInt.empty());
            assertEquals(k.slot(), sticky.slot(), "the slot host-k held last, though slot " + x.slot() + " is lower");
            assertTrue(sticky.fence() > k.fence());
        }
    }

    /** Basics: without preferred or sticky slot, the lowest free slot is taken. */
    @Test
    public void acquireTakesTheLowestFreeSlot() {
        String namespace = newNamespace();
        try (SlotLeaseStore store = newStore()) {
            assertEquals(0, acquiredFor(AcquireReason.LOWEST, store, namespace, owner("host-a"), 4, OptionalInt.empty()).slot());
            SlotLease b = acquiredFor(AcquireReason.LOWEST, store, namespace, owner("host-b"), 4, OptionalInt.empty());
            assertEquals(1, b.slot());
            assertEquals(2, acquiredFor(AcquireReason.LOWEST, store, namespace, owner("host-c"), 4, OptionalInt.empty()).slot());
            assertTrue(store.release(b, b.ceiling()));
            assertEquals(1, acquiredFor(AcquireReason.LOWEST, store, namespace, owner("host-d"), 4, OptionalInt.empty()).slot());
            assertEquals(3, acquiredFor(AcquireReason.LOWEST, store, namespace, owner("host-e"), 4, OptionalInt.empty()).slot());
        }
    }

    /** Basics: acquire flags a live lease of the same owner key under another incarnation, and only a live one. */
    @Test
    public void acquireFlagsDuplicateOwnerKeys() {
        String namespace = newNamespace();
        try (SlotLeaseStore store = newStore()) {
            Owner first = owner("host-a");
            assertFalse(acquiredResult(store, namespace, first, 4, LONG_TTL, OptionalInt.empty()).duplicateOwnerKey());
            assertFalse(acquiredResult(store, namespace, owner("host-b"), 4, LONG_TTL, OptionalInt.empty()).duplicateOwnerKey(), "another key is no duplicate");
            AcquireResult.Acquired second = acquiredResult(store, namespace, owner("host-a"), 4, LONG_TTL, OptionalInt.empty());
            assertTrue(second.duplicateOwnerKey(), "host-a already holds a live lease under another incarnation");
            AcquireResult.Acquired retry = acquiredResult(store, namespace, first, 4, LONG_TTL, OptionalInt.empty());
            assertEquals(AcquireReason.IDEMPOTENT, retry.reason());
            assertTrue(retry.duplicateOwnerKey(), "the retry of the first incarnation sees the second one");
            assertTrue(store.release(second.lease(), second.lease().ceiling()));
            assertTrue(store.release(retry.lease(), retry.lease().ceiling()));
            assertFalse(acquiredResult(store, namespace, owner("host-a"), 4, LONG_TTL, OptionalInt.empty()).duplicateOwnerKey(),
                "released leases of the key are no duplicates");
        }
        String expiring = newNamespace();
        try (SlotLeaseStore store = newStore()) {
            acquired(store, expiring, owner("host-a"), 1, shortTtl(), OptionalInt.empty());
            assertFalse(awaitAcquiredResult(store, expiring, owner("host-a"), 1, LONG_TTL).duplicateOwnerKey(),
                "an expired lease of the key is no duplicate");
        }
    }

    /** Basics: with every slot below the limit held, acquire returns Exhausted with the earliest expiry. */
    @Test
    public void acquireReturnsExhaustedWhenEverySlotIsHeld() {
        String namespace = newNamespace();
        try (SlotLeaseStore store = newStore()) {
            SlotLease a = acquired(store, namespace, owner("host-a"), 2, LONG_TTL, OptionalInt.empty());
            SlotLease b = acquired(store, namespace, owner("host-b"), 2, LONG_TTL.multipliedBy(2), OptionalInt.empty());
            AcquireResult result = store.acquire(namespace, owner("host-c"), 2, LONG_TTL, TOLERANCE, OptionalInt.empty());
            AcquireResult.Exhausted exhausted = assertInstanceOf(AcquireResult.Exhausted.class, result);
            assertEquals(Math.min(a.expiresAt(), b.expiresAt()), exhausted.earliestExpiresAt());
            assertInstanceOf(AcquireResult.Exhausted.class, store.acquire(namespace, owner("host-c"), 2, LONG_TTL, TOLERANCE, OptionalInt.of(0)),
                "a held preferred slot does not help");
            assertTrue(store.release(a, a.ceiling()));
            assertEquals(a.slot(), acquired(store, namespace, owner("host-c"), 2).slot());
        }
    }

    /** Basics: acquired slots are within [0, limit), for limit 1 and 2^bits. */
    @Test
    public void acquireStaysBelowTheLimit() {
        try (SlotLeaseStore store = newStore()) {
            String single = newNamespace();
            assertEquals(0, acquired(store, single, owner("host-a"), 1).slot());
            assertInstanceOf(AcquireResult.Exhausted.class, store.acquire(single, owner("host-b"), 1, LONG_TTL, TOLERANCE, OptionalInt.empty()));

            String sixteen = newNamespace();
            int limit = 1 << 4;
            Set<Integer> slots = new HashSet<>();
            for (int i = 0; i < limit; i++) {
                int slot = acquired(store, sixteen, owner("host-" + i), limit).slot();
                assertTrue(slot >= 0 && slot < limit, "slot " + slot + " is below the limit " + limit);
                slots.add(slot);
            }
            assertEquals(limit, slots.size());
            assertInstanceOf(AcquireResult.Exhausted.class, store.acquire(sixteen, owner("host-x"), limit, LONG_TTL, TOLERANCE, OptionalInt.empty()));

            String wide = newNamespace();
            assertEquals(1023, acquired(store, wide, owner("host-a"), 1 << 10, LONG_TTL, OptionalInt.of(1023)).slot());
        }
    }

    /** Safety: after expiry, the floor of the next holder is at least the last ceiling stored for the previous holder. */
    @Test
    public void takeoverFloorIsAtLeastThePreviousStoredCeiling() {
        String namespace = newNamespace();
        try (SlotLeaseStore store = newStore(); SlotLeaseStore other = newStore()) {
            SlotLease previous = acquired(store, namespace, owner("host-a"), 1, shortTtl(), OptionalInt.empty());
            previous = renewed(store, previous, shortTtl());
            previous = renewed(store, previous, shortTtl());
            SlotLease next = awaitAcquired(other, namespace, owner("host-b"), 1, LONG_TTL);
            assertEquals(previous.slot(), next.slot());
            assertTrue(next.fence() > previous.fence(), "a takeover raises the fence");
            assertTrue(next.floor() >= previous.ceiling(), "floor " + next.floor() + " of the next holder is at least the previous ceiling " + previous.ceiling());
            assertTrue(next.ceiling() >= next.floor());
        }
    }

    /** Safety: after release(lease, lastIssued), the floor of the next holder is at least min(lastIssued, ceiling) and at least the released floor. */
    @Test
    public void floorAfterReleaseIsAtLeastLastIssued() {
        String namespace = newNamespace();
        try (SlotLeaseStore store = newStore()) {
            SlotLease a = acquired(store, namespace, owner("host-a"), 1);
            long lastIssued = a.ceiling() - 1_000;
            assertTrue(store.release(a, lastIssued));
            SlotLease b = acquired(store, namespace, owner("host-b"), 1);
            assertTrue(b.floor() >= Math.max(a.floor(), lastIssued), "floor " + b.floor() + " after a release at " + lastIssued);
            assertTrue(b.fence() > a.fence(), "a release raises the fence");

            assertTrue(store.release(b, b.ceiling() + 60_000));
            SlotLease c = acquired(store, namespace, owner("host-c"), 1);
            assertTrue(c.floor() >= b.ceiling(), "a last issued beyond the ceiling counts as the ceiling");

            assertTrue(store.release(c, Long.MIN_VALUE));
            SlotLease d = acquired(store, namespace, owner("host-d"), 1);
            assertTrue(d.floor() >= c.floor(), "nothing issued keeps the released floor");
        }
    }

    /** Safety: over renewals, releases and takeovers, ceiling and the next floor of a slot never go down. */
    @Test
    public void ceilingAndNextFloorNeverDecrease() {
        String namespace = newNamespace();
        try (SlotLeaseStore store = newStore()) {
            List<SlotLease> holders = new ArrayList<>();
            List<SlotLease> leases = new ArrayList<>();
            SlotLease a = acquired(store, namespace, owner("host-a"), 1, shortTtl(), OptionalInt.empty());
            holders.add(a);
            leases.add(a);
            SlotLease renewed = renewed(store, a, shortTtl());
            leases.add(renewed);
            SlotLease shorter = renewed(store, renewed, Duration.ofMillis(1));
            assertTrue(shorter.expiresAt() >= renewed.expiresAt(), "a shorter ttl does not move the expiry back");
            leases.add(shorter);
            assertTrue(store.release(shorter, shorter.floor() + 1));

            SlotLease b = acquired(store, namespace, owner("host-b"), 1, shortTtl(), OptionalInt.empty());
            holders.add(b);
            leases.add(b);
            SlotLease renewedB = renewed(store, b, shortTtl());
            leases.add(renewedB);

            SlotLease c = awaitAcquired(store, namespace, owner("host-c"), 1, shortTtl());
            holders.add(c);
            leases.add(c);
            assertTrue(c.floor() >= renewedB.ceiling(), "the takeover starts at the last stored ceiling");

            for (int i = 1; i < holders.size(); i++) {
                assertTrue(holders.get(i).floor() >= holders.get(i - 1).floor(), "floors never go down: " + holders);
            }
            for (int i = 1; i < leases.size(); i++) {
                assertTrue(leases.get(i).ceiling() >= leases.get(i - 1).ceiling(), "ceilings never go down: " + leases);
            }
        }
    }

    /** Safety: a live lease is never taken over by the same owner key with another incarnation. */
    @Test
    public void sameKeyWithAnotherIncarnationNeverTakesOver() {
        String namespace = newNamespace();
        try (SlotLeaseStore store = newStore()) {
            SlotLease first = acquired(store, namespace, owner("host-a"), 2);
            AcquireResult.Acquired secondResult = acquiredResult(store, namespace, owner("host-a"), 2, LONG_TTL, OptionalInt.empty());
            assertTrue(secondResult.duplicateOwnerKey());
            SlotLease second = secondResult.lease();
            assertNotEquals(first.slot(), second.slot());
            AcquireResult third = store.acquire(namespace, owner("host-a"), 2, LONG_TTL, TOLERANCE, OptionalInt.of(first.slot()));
            assertInstanceOf(AcquireResult.Exhausted.class, third);
            assertTrue(store.renew(first, LONG_TTL, TOLERANCE).isPresent(), "the first incarnation still holds its slot");
        }
    }

    /** Safety: once taken over, renew of the previous holder returns empty and its release returns false. */
    @Test
    public void expiredHolderCannotRenewOrRelease() {
        String namespace = newNamespace();
        try (SlotLeaseStore store = newStore(); SlotLeaseStore other = newStore()) {
            SlotLease previous = acquired(store, namespace, owner("host-a"), 1, shortTtl(), OptionalInt.empty());
            SlotLease next = awaitAcquired(other, namespace, owner("host-b"), 1, LONG_TTL);
            assertEquals(Optional.empty(), store.renew(previous, LONG_TTL, TOLERANCE));
            assertFalse(store.release(previous, previous.ceiling()));
            SlotLease nextRenewed = renewed(other, next, LONG_TTL);
            assertEquals(next.fence(), nextRenewed.fence(), "the failed calls of the previous holder left the lease alone");
            assertTrue(other.release(nextRenewed, nextRenewed.ceiling()));
        }
    }

    /** Safety: replaying a renew keeps the fence and succeeds. */
    @Test
    public void renewIsIdempotent() {
        String namespace = newNamespace();
        try (SlotLeaseStore store = newStore()) {
            SlotLease lease = acquired(store, namespace, owner("host-a"), 1);
            SlotLease first = renewed(store, lease, LONG_TTL);
            // The response of the first renew is "lost": the client replays the request with the lease it had.
            SlotLease replayed = renewed(store, lease, LONG_TTL);
            assertEquals(lease.fence(), first.fence());
            assertEquals(lease.fence(), replayed.fence());
            assertEquals(lease.floor(), replayed.floor());
            assertTrue(replayed.ceiling() >= first.ceiling());
            assertTrue(renewed(store, first, LONG_TTL).ceiling() >= replayed.ceiling());
            assertTrue(store.release(lease, lease.ceiling()), "the original lease still names the holding");
        }
    }

    /** Clock: storeNow is the store clock, not the client clock. */
    @Test
    public void storeNowComesFromTheStore() {
        String namespace = newNamespace();
        try (SlotLeaseStore store = newStore()) {
            Optional<ManualClock> clock = storeClock();
            if (clock.isPresent()) {
                long storeTime = System.currentTimeMillis() + Duration.ofDays(10).toMillis();
                clock.get().set(storeTime);
                SlotLease lease = acquired(store, namespace, owner("host-a"), 1);
                assertEquals(storeTime, lease.storeNow());
                assertEquals(storeTime + LONG_TTL.toMillis(), lease.expiresAt());
                clock.get().set(storeTime + 5_000);
                assertEquals(storeTime + 5_000, renewed(store, lease, LONG_TTL).storeNow());
                return;
            }
            long before = System.currentTimeMillis();
            SlotLease lease = acquired(store, namespace, owner("host-a"), 1);
            long after = System.currentTimeMillis();
            long bound = maxStoreClockOffset().toMillis();
            assertTrue(lease.storeNow() >= before - bound && lease.storeNow() <= after + bound,
                "storeNow " + lease.storeNow() + " is epoch milliseconds of the store, near the test clock [" + before + ", " + after + "]");
        }
    }

    /** Clock: expiresAt minus storeNow equals ttl: both come from the same write. */
    @Test
    public void expiresAtIsTtlAfterStoreNow() {
        String namespace = newNamespace();
        try (SlotLeaseStore store = newStore()) {
            SlotLease lease = acquired(store, namespace, owner("host-a"), 1);
            assertEquals(LONG_TTL.toMillis(), lease.expiresAt() - lease.storeNow());
            assertEquals(lease.expiresAt() + TOLERANCE.toMillis(), lease.ceiling(), "on a new slot the ceiling is expiresAt + tolerance");
            SlotLease renewed = renewed(store, lease, LONG_TTL.multipliedBy(2));
            assertEquals(LONG_TTL.multipliedBy(2).toMillis(), renewed.expiresAt() - renewed.storeNow());
            assertEquals(renewed.expiresAt() + TOLERANCE.toMillis(), renewed.ceiling());
        }
    }

    /** Concurrency: N clients acquiring in one namespace at once get pairwise distinct slots. */
    @Test
    public void concurrentAcquiresGetDistinctSlots() {
        String namespace = newNamespace();
        int clients = concurrentClients();
        int limit = clients * 2 / 3;
        List<SlotLeaseStore> stores = Concurrently.open(storeInstances(), this::newStore);
        try {
            List<AcquireResult> results = Concurrently.run(clients,
                i -> stores.get(i % stores.size()).acquire(namespace, owner("host-" + i), limit, LONG_TTL, TOLERANCE, OptionalInt.empty()));
            Set<Integer> slots = new HashSet<>();
            int exhausted = 0;
            for (AcquireResult result : results) {
                if (result instanceof AcquireResult.Acquired acquired) {
                    assertTrue(slots.add(acquired.lease().slot()), () -> "slot " + acquired.lease().slot() + " was acquired twice: " + results);
                } else {
                    exhausted++;
                }
            }
            assertEquals(limit, slots.size(), () -> "every slot is taken: " + results);
            assertEquals(clients - limit, exhausted);

            String shared = newNamespace();
            List<AcquireResult> sameKey = Concurrently.run(limit,
                i -> stores.get(i % stores.size()).acquire(shared, owner("same-host"), limit, LONG_TTL, TOLERANCE, OptionalInt.of(0)));
            Set<Integer> sameKeySlots = new HashSet<>();
            for (AcquireResult result : sameKey) {
                assertTrue(sameKeySlots.add(assertInstanceOf(AcquireResult.Acquired.class, result).lease().slot()),
                    () -> "incarnations of one key got the same slot: " + sameKey);
            }
        } finally {
            Concurrently.closeAll(stores);
        }
    }

    /** Metadata: acquiring with another limit or tolerance than the namespace was first used with throws NamespaceMismatchException. */
    @Test
    public void rejectsMismatchedNamespaceParameters() {
        String namespace = newNamespace();
        try (SlotLeaseStore store = newStore(); SlotLeaseStore other = newStore()) {
            acquired(store, namespace, owner("host-a"), 8);
            assertThrows(NamespaceMismatchException.class,
                () -> other.acquire(namespace, owner("host-b"), 16, LONG_TTL, TOLERANCE, OptionalInt.empty()));
            assertThrows(NamespaceMismatchException.class,
                () -> other.acquire(namespace, owner("host-b"), 8, LONG_TTL, TOLERANCE.multipliedBy(2), OptionalInt.empty()));
            acquired(other, namespace, owner("host-b"), 8);
        }
    }
}
