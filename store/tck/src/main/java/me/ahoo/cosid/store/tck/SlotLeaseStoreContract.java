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

import me.ahoo.cosid.lease.SlotLeaseStore;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;

/**
 * The {@link SlotLeaseStore} contract (05-machine-lease.md §2, 06-stores.md §3). A backend test extends it and
 * implements {@link #newStore()}.
 */
public abstract class SlotLeaseStoreContract {
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

    /** Basics: acquiring again with the same key and incarnation returns the same slot and fence. */
    @Test
    @Disabled("R1-C: acquiring again with the same key and incarnation returns the same slot and fence")
    public void acquireIsIdempotentForTheSameOwner() {
        throw new UnsupportedOperationException("R1-C");
    }

    /** Basics: a free preferred slot is taken before the sticky and the lowest ones. */
    @Test
    @Disabled("R1-C: a free preferred slot is taken before the sticky and the lowest ones")
    public void acquireTakesThePreferredSlot() {
        throw new UnsupportedOperationException("R1-C");
    }

    /** Basics: a new incarnation of an owner key gets back the free slot that key held last. */
    @Test
    @Disabled("R1-C: a new incarnation of an owner key gets back the free slot that key held last")
    public void acquireTakesTheStickySlotOfTheOwnerKey() {
        throw new UnsupportedOperationException("R1-C");
    }

    /** Basics: without preferred or sticky slot, the lowest free slot is taken. */
    @Test
    @Disabled("R1-C: without preferred or sticky slot, the lowest free slot is taken")
    public void acquireTakesTheLowestFreeSlot() {
        throw new UnsupportedOperationException("R1-C");
    }

    /** Basics: with every slot below the limit held, acquire returns Exhausted with the earliest expiry. */
    @Test
    @Disabled("R1-C: with every slot below the limit held, acquire returns Exhausted with the earliest expiry")
    public void acquireReturnsExhaustedWhenEverySlotIsHeld() {
        throw new UnsupportedOperationException("R1-C");
    }

    /** Basics: acquired slots are within [0, limit), for limit 1 and 2^bits. */
    @Test
    @Disabled("R1-C: acquired slots are within [0, limit), for limit 1 and 2^bits")
    public void acquireStaysBelowTheLimit() {
        throw new UnsupportedOperationException("R1-C");
    }

    /** Safety: after expiry, the floor of the next holder is at least the last ceiling stored for the previous holder. */
    @Test
    @Disabled("R1-C: after expiry, the floor of the next holder is at least the last ceiling stored for the previous holder")
    public void takeoverFloorIsAtLeastThePreviousStoredCeiling() {
        throw new UnsupportedOperationException("R1-C");
    }

    /** Safety: after release(lease, lastIssued), the floor of the next holder is at least min(lastIssued, ceiling) and at least the released floor. */
    @Test
    @Disabled("R1-C: after release(lease, lastIssued), the floor of the next holder is at least min(lastIssued, ceiling) and at least the released floor")
    public void floorAfterReleaseIsAtLeastLastIssued() {
        throw new UnsupportedOperationException("R1-C");
    }

    /** Safety: over renewals, releases and takeovers, ceiling and the next floor of a slot never go down. */
    @Test
    @Disabled("R1-C: over renewals, releases and takeovers, ceiling and the next floor of a slot never go down")
    public void ceilingAndNextFloorNeverDecrease() {
        throw new UnsupportedOperationException("R1-C");
    }

    /** Safety: a live lease is never taken over by the same owner key with another incarnation. */
    @Test
    @Disabled("R1-C: a live lease is never taken over by the same owner key with another incarnation")
    public void sameKeyWithAnotherIncarnationNeverTakesOver() {
        throw new UnsupportedOperationException("R1-C");
    }

    /** Safety: once taken over, renew of the previous holder returns empty and its release returns false. */
    @Test
    @Disabled("R1-C: once taken over, renew of the previous holder returns empty and its release returns false")
    public void expiredHolderCannotRenewOrRelease() {
        throw new UnsupportedOperationException("R1-C");
    }

    /** Safety: replaying a renew keeps the fence and succeeds. */
    @Test
    @Disabled("R1-C: replaying a renew keeps the fence and succeeds")
    public void renewIsIdempotent() {
        throw new UnsupportedOperationException("R1-C");
    }

    /** Clock: storeNow is the store clock, not the client clock. */
    @Test
    @Disabled("R1-C: storeNow is the store clock, not the client clock")
    public void storeNowComesFromTheStore() {
        throw new UnsupportedOperationException("R1-C");
    }

    /** Clock: expiresAt minus storeNow equals ttl: both come from the same write. */
    @Test
    @Disabled("R1-C: expiresAt minus storeNow equals ttl: both come from the same write")
    public void expiresAtIsTtlAfterStoreNow() {
        throw new UnsupportedOperationException("R1-C");
    }

    /** Concurrency: N clients acquiring in one namespace at once get pairwise distinct slots. */
    @Test
    @Disabled("R1-C: N clients acquiring in one namespace at once get pairwise distinct slots")
    public void concurrentAcquiresGetDistinctSlots() {
        throw new UnsupportedOperationException("R1-C");
    }

    /** Metadata: acquiring with another limit or tolerance than the namespace was first used with throws NamespaceMismatchException. */
    @Test
    @Disabled("R1-C: acquiring with another limit or tolerance than the namespace was first used with throws NamespaceMismatchException")
    public void rejectsMismatchedNamespaceParameters() {
        throw new UnsupportedOperationException("R1-C");
    }
}
