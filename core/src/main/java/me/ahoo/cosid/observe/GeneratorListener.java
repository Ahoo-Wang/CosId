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

package me.ahoo.cosid.observe;

import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.segment.Range;

import java.time.Duration;

/**
 * Generator, lease and store events (07-runtime-and-spring.md §2). Counting and metrics are implementations of it.
 *
 * <p>The contract of an implementation (R1-core-09):
 * <ul>
 *   <li><b>Threads.</b> A callback runs on the thread that did the work, concurrently with any other callback:
 *   {@link #onRangeReserved} and {@link #onRefillFailed} on the refill executor, or on a caller that made the
 *   reservation itself; {@link #onBatchResized}, {@link #onSequenceExhausted}, {@link #onClockRegression} and
 *   {@link #onWindowExceeded} on a caller of {@code generate()}, while it switches ranges or waits; the slot callbacks
 *   on the thread that acquires, renews or releases (the start, the runtime's renewal task, the close);
 *   {@link #onStoreTimeout} on the thread whose store call timed out. Implementations are thread-safe.</li>
 *   <li><b>No blocking.</b> None runs under a generator's lock, but a callback on a caller delays that caller's id,
 *   and one on the renewal task delays the renewal: record and return, hand anything slow to another thread.</li>
 *   <li><b>Not per id.</b> No callback runs for an id taken from a range or a time unit at hand; only on range
 *   switches, refills, waits and lease changes.</li>
 *   <li><b>Failures.</b> The runtime calls the listener of {@code RuntimeOptions} through a wrapper: a callback that
 *   throws is logged and never fails the generator, refill or renewal that raised it.</li>
 * </ul>
 */
public interface GeneratorListener {
    GeneratorListener NOOP = new GeneratorListener() {
    };

    /**
     * A segment generator reserved a range from its store.
     *
     * @param generator the generator
     * @param key       the range key
     * @param range     the range reserved
     * @param latency   how long the store call took
     */
    default void onRangeReserved(String generator, String key, Range range, Duration latency) {
    }

    /**
     * A reservation failed; the generator retries after a backoff, and callers with no value left fail meanwhile.
     *
     * @param generator the generator
     * @param key       the range key
     * @param error     why
     */
    default void onRefillFailed(String generator, String key, Throwable error) {
    }

    /**
     * The rate estimate changed the size of the next reservations (a segment with a lookahead).
     *
     * @param generator the generator
     * @param oldSize   the batch before
     * @param newSize   the batch now
     */
    default void onBatchResized(String generator, long oldSize, long newSize) {
    }

    /**
     * A time-based generator used up the sequence of its time unit and waits for the next one: once per such wait.
     *
     * @param generator the generator
     */
    default void onSequenceExhausted(String generator) {
    }

    /**
     * The clock went back.
     *
     * @param generator       the generator
     * @param backwardsMillis how far back
     * @param action          {@code borrow}, {@code wait} or {@code fail}
     */
    default void onClockRegression(String generator, long backwardsMillis, String action) {
    }

    /**
     * A time-based generator reached the ceiling of its slot window (renewals are failing or late) and waits for a
     * renewal: once per such wait.
     *
     * @param generator the generator
     */
    default void onWindowExceeded(String generator) {
    }

    /**
     * A slot was acquired.
     *
     * @param lease  the lease
     * @param reason {@code idempotent}, {@code preferred}, {@code sticky} or {@code lowest}
     */
    default void onSlotAcquired(SlotLease lease, String reason) {
    }

    /**
     * A slot lease was renewed: its window moved on.
     *
     * @param lease the renewed lease
     */
    default void onSlotRenewed(SlotLease lease) {
    }

    /**
     * A renewal failed; the lease stays valid until its expiry, and the next renewal tries again.
     *
     * @param lease the lease as held
     * @param error why
     */
    default void onSlotRenewFailed(SlotLease lease, Throwable error) {
    }

    /**
     * The store no longer has the slot for this owner (it expired, or another owner took it): its generators stop at
     * the window ceiling while a slot is acquired again.
     *
     * @param lease the lease that was lost
     */
    default void onSlotLost(SlotLease lease) {
    }

    /**
     * A slot was released, at the close, with the largest timestamp its generators issued.
     *
     * @param lease the lease released
     */
    default void onSlotReleased(SlotLease lease) {
    }

    /**
     * The store clock differs from this host's by more than half the tolerance, measured at a lease call.
     *
     * @param namespace  the namespace of the slot
     * @param skewMillis the store clock minus this host's (at the middle of the call), in milliseconds: positive when
     *                   the store is ahead
     */
    default void onClockSkew(String namespace, long skewMillis) {
    }

    /**
     * The acquired slot's owner key is also held by a live lease of another incarnation: two processes use one owner
     * key (a copied {@code owner-key}), which is legal but defeats the preferred slot.
     *
     * @param namespace the namespace
     * @param ownerKey  the owner key
     */
    default void onDuplicateOwnerKey(String namespace, String ownerKey) {
    }

    /**
     * A store took too long: a call failed with the store's own timeout ({@link me.ahoo.cosid.store.StoreTimeoutException},
     * itself or as a cause), operation {@code reserve}, {@code seal}, {@code acquire}, {@code renew} or
     * {@code release}; or callers waited for a segment refill longer than the reserve timeout, operation
     * {@code reserve-wait}, once per refill however many callers gave up.
     *
     * @param store     the store name
     * @param operation the operation
     */
    default void onStoreTimeout(String store, String operation) {
    }
}
