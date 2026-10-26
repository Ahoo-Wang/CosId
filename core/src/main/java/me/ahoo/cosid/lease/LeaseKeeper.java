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

import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.time.TimeSource;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.Executor;

/**
 * Holds the slot lease of a runtime (05-machine-lease.md §4.2): acquires it at {@link #start()}, renews it every
 * {@code ttl/3} with backoff, re-acquires a lost slot and publishes each holding as a {@link SlotBinding}.
 */
public final class LeaseKeeper implements SlotHandle, AutoCloseable {
    private final SlotLeaseStore store;
    private final String namespace;
    private final Owner owner;
    private final int bits;
    private final Duration ttl;
    private final Duration tolerance;
    private final OptionalInt preferredSlot;
    private final Duration acquireTimeout;
    private final Executor executor;
    private final TimeSource time;
    private final GeneratorListener listener;

    /**
     * A lease keeper; {@link #start()} acquires the slot.
     *
     * @param store          the lease store
     * @param namespace      the namespace
     * @param owner          the owner, with this runtime's incarnation
     * @param bits           the slots are {@code [0, 2^bits)}
     * @param ttl            the lease time to live; default 30s
     * @param tolerance      the ceiling lies this far beyond the expiry (D20); default 1s
     * @param preferredSlot  the slot to try first
     * @param acquireTimeout how long {@link #start()} retries an exhausted namespace; default {@code ttl}
     * @param executor       runs the renewals; the runtime's own executor by default
     * @param time           the time source
     * @param listener       the event listener
     */
    public LeaseKeeper(SlotLeaseStore store, String namespace, Owner owner, int bits, Duration ttl, Duration tolerance,
                       OptionalInt preferredSlot, Duration acquireTimeout, Executor executor, TimeSource time,
                       GeneratorListener listener) {
        this.store = Objects.requireNonNull(store, "store");
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.bits = bits;
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.tolerance = Objects.requireNonNull(tolerance, "tolerance");
        this.preferredSlot = Objects.requireNonNull(preferredSlot, "preferredSlot");
        this.acquireTimeout = Objects.requireNonNull(acquireTimeout, "acquireTimeout");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.time = Objects.requireNonNull(time, "time");
        this.listener = Objects.requireNonNull(listener, "listener");
        if (bits < 1 || bits > Integer.SIZE - 2) {
            throw new IllegalArgumentException("bits must be in [1, " + (Integer.SIZE - 2) + "]: " + bits);
        }
    }

    public String namespace() {
        return namespace;
    }

    public Owner owner() {
        return owner;
    }

    public int bits() {
        return bits;
    }

    /**
     * Acquires the slot and starts renewing it.
     *
     * @throws me.ahoo.cosid.CosIdException if no slot is acquired within the acquire timeout
     */
    public void start() {
        throw new UnsupportedOperationException("R1-C");
    }

    @Override
    public SlotBinding current() {
        throw new UnsupportedOperationException("R1-C");
    }

    /**
     * The lease as last returned by the store.
     *
     * @return the lease, or empty before the first acquisition and after release
     */
    public Optional<SlotLease> lease() {
        throw new UnsupportedOperationException("R1-C");
    }

    /**
     * Stops renewing and releases the lease with the exact last issued timestamp (§4.3). Call it once every
     * generator on the slot is closed.
     *
     * @param lastIssued the largest timestamp issued on the slot, in milliseconds
     */
    public void close(long lastIssued) {
        throw new UnsupportedOperationException("R1-C");
    }

    /**
     * Stops renewing and releases the lease as if issued up to its ceiling, for a slot no generator used.
     */
    @Override
    public void close() {
        throw new UnsupportedOperationException("R1-C");
    }
}
