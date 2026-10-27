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

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.NamespaceMismatchException;
import me.ahoo.cosid.internal.Ticker;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.time.TimeSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Holds the slot lease of a runtime (05-machine-lease.md §4.2): acquires it at {@link #start()}, renews it every
 * {@code ttl/3} with backoff, re-acquires a lost slot and publishes each holding as a {@link SlotBinding}.
 *
 * <p>The renewals run in one long-lived task on the executor, which must run tasks asynchronously (the runtime's
 * default runs each on a virtual thread). Their schedule follows elapsed time, not the wall clock, so a clock step
 * does not delay a renewal.
 */
public final class LeaseKeeper implements SlotHandle, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(LeaseKeeper.class);
    private static final long FIRST_BACKOFF_NANOS = TimeUnit.MILLISECONDS.toNanos(100);
    private static final long MIN_RETRY_NANOS = TimeUnit.MILLISECONDS.toNanos(10);
    /** The incarnations held by started keepers of this JVM, per namespace: each keeper needs its own (§2). */
    private static final Set<String> INCARNATIONS = ConcurrentHashMap.newKeySet();

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
    private final Ticker ticker;
    private final long renewIntervalNanos;
    private final long maxBackoffNanos;
    private final String incarnationKey;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition wakeUp = lock.newCondition();
    private volatile State state = State.NEW;
    private volatile SlotBinding binding;
    private volatile SlotLease lease;
    /** The store clock minus the local clock, as of the last response; only for waits and the skew event. */
    private volatile long skewMillis;
    private boolean registered;
    private long generation;
    private long nextDueNanos;
    private long backoffNanos;
    private volatile Thread starter;
    private volatile boolean ranInline;

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
        this(store, namespace, owner, bits, ttl, tolerance, preferredSlot, acquireTimeout, executor, time, listener, Ticker.SYSTEM);
    }

    LeaseKeeper(SlotLeaseStore store, String namespace, Owner owner, int bits, Duration ttl, Duration tolerance,
                OptionalInt preferredSlot, Duration acquireTimeout, Executor executor, TimeSource time,
                GeneratorListener listener, Ticker ticker) {
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
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        if (bits < 1 || bits > Integer.SIZE - 2) {
            throw new IllegalArgumentException("bits must be in [1, " + (Integer.SIZE - 2) + "]: " + bits);
        }
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive: " + ttl);
        }
        if (tolerance.isNegative()) {
            throw new IllegalArgumentException("tolerance must not be negative: " + tolerance);
        }
        if (acquireTimeout.isNegative()) {
            throw new IllegalArgumentException("acquireTimeout must not be negative: " + acquireTimeout);
        }
        if (preferredSlot.isPresent() && (preferredSlot.getAsInt() < 0 || preferredSlot.getAsInt() >= 1 << bits)) {
            throw new IllegalArgumentException("preferredSlot must be in [0, " + (1 << bits) + "): " + preferredSlot.getAsInt());
        }
        this.renewIntervalNanos = ttl.toNanos() / 3;
        this.maxBackoffNanos = Math.max(1, ttl.toNanos() / 6);
        this.incarnationKey = namespace + '/' + owner.incarnation();
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
        lock.lock();
        try {
            if (state != State.NEW) {
                throw new IllegalStateException("LeaseKeeper of namespace [" + namespace + "] was already started");
            }
            if (!INCARNATIONS.add(incarnationKey)) {
                throw new IllegalStateException("Another LeaseKeeper of namespace [" + namespace + "] holds incarnation "
                    + owner.incarnation() + ": each keeper needs its own");
            }
            registered = true;
            state = State.STARTING;
        } finally {
            lock.unlock();
        }
        AcquireResult.Acquired acquired;
        try {
            acquired = acquireWithin(acquireTimeout);
        } catch (RuntimeException e) {
            close(Long.MIN_VALUE);
            throw e;
        }
        lock.lock();
        try {
            if (state != State.STARTING) {
                releaseQuietly(acquired.lease(), acquired.lease().floor());
                throw new IllegalStateException("LeaseKeeper of namespace [" + namespace + "] was closed while starting");
            }
            publish(acquired);
            nextDueNanos = ticker.nanoTime() + renewIntervalNanos;
            state = State.RUNNING;
            starter = Thread.currentThread();
        } finally {
            lock.unlock();
        }
        try {
            executor.execute(this::renewLoop);
        } catch (RejectedExecutionException e) {
            close(Long.MIN_VALUE);
            throw new CosIdException("The executor rejected the renewals of namespace [" + namespace + "]", e);
        } finally {
            starter = null;
        }
        if (ranInline) {
            close(Long.MIN_VALUE);
            throw new IllegalArgumentException("The executor of LeaseKeeper must run tasks asynchronously, not on the calling thread");
        }
    }

    @Override
    public SlotBinding current() {
        SlotBinding current = binding;
        if (current == null) {
            throw new IllegalStateException("LeaseKeeper of namespace [" + namespace + "] is not started");
        }
        return current;
    }

    /**
     * The lease as last returned by the store.
     *
     * @return the lease, or empty before the first acquisition and after release
     */
    public Optional<SlotLease> lease() {
        return Optional.ofNullable(lease);
    }

    /**
     * Stops renewing and releases the lease with the exact last issued timestamp (§4.3). Call it once every
     * generator on the slot is closed.
     *
     * @param lastIssued the largest timestamp issued on the slot, in milliseconds
     */
    public void close(long lastIssued) {
        SlotLease held;
        lock.lock();
        try {
            if (state == State.CLOSED) {
                return;
            }
            state = State.CLOSED;
            wakeUp.signalAll();
            held = lease;
            lease = null;
            SlotBinding current = binding;
            if (current != null) {
                current.invalidate();
            }
            if (registered) {
                INCARNATIONS.remove(incarnationKey);
                registered = false;
            }
        } finally {
            lock.unlock();
        }
        if (held != null) {
            releaseQuietly(held, lastIssued);
        }
    }

    /**
     * Stops renewing and releases the lease as if issued up to its ceiling, for a slot no generator used.
     */
    @Override
    public void close() {
        // The store caps lastIssued at the ceiling it holds, which covers a renewal whose response was lost.
        close(Long.MAX_VALUE);
    }

    /** Retries acquire until a slot is free or {@code timeout} passes; a namespace mismatch fails at once. */
    private AcquireResult.Acquired acquireWithin(Duration timeout) {
        long deadline = ticker.nanoTime() + timeout.toNanos();
        long backoff = 0;
        RuntimeException lastError = null;
        while (true) {
            if (state == State.CLOSED) {
                throw new IllegalStateException("LeaseKeeper of namespace [" + namespace + "] was closed while starting");
            }
            long waitNanos;
            try {
                AcquireResult result = acquireOnce();
                if (result instanceof AcquireResult.Acquired acquired) {
                    return acquired;
                }
                waitNanos = untilExpiry((AcquireResult.Exhausted) result);
                lastError = null;
                log.info("Namespace [{}] has no free slot; retrying in {}ms", namespace, TimeUnit.NANOSECONDS.toMillis(waitNanos));
            } catch (NamespaceMismatchException e) {
                throw e;
            } catch (RuntimeException e) {
                lastError = e;
                backoff = nextBackoff(backoff);
                waitNanos = backoff;
                log.warn("Acquiring a slot in namespace [{}] failed; retrying in {}ms", namespace, TimeUnit.NANOSECONDS.toMillis(waitNanos), e);
            }
            long remaining = deadline - ticker.nanoTime();
            if (remaining <= 0) {
                throw new CosIdException("No slot acquired in namespace [" + namespace + "] within " + timeout
                    + (lastError == null ? ": every slot is held" : ": the store failed"), lastError);
            }
            try {
                ticker.sleep(Math.min(waitNanos, remaining));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CosIdException("Interrupted while acquiring a slot in namespace [" + namespace + "]", e);
            }
        }
    }

    private AcquireResult acquireOnce() {
        long sentAt = time.currentMillis();
        AcquireResult result = store.acquire(namespace, owner, 1 << bits, ttl, tolerance, preferredSlot);
        if (result instanceof AcquireResult.Acquired acquired) {
            observeClock(acquired.lease(), sentAt);
        }
        return result;
    }

    /** How long until the earliest lease of an exhausted namespace expires, on the store clock as last estimated. */
    private long untilExpiry(AcquireResult.Exhausted exhausted) {
        long storeNow = time.currentMillis() + skewMillis;
        long millis = Math.max(0, exhausted.earliestExpiresAt() - storeNow + 1);
        return Math.min(Math.max(MIN_RETRY_NANOS, TimeUnit.MILLISECONDS.toNanos(millis)), Math.max(MIN_RETRY_NANOS, renewIntervalNanos));
    }

    private long nextBackoff(long current) {
        return Math.min(current == 0 ? FIRST_BACKOFF_NANOS : current * 2, maxBackoffNanos);
    }

    /** Health only (§4.2): the store clock against the local clock at the middle of the call. Safety never reads it. */
    private void observeClock(SlotLease received, long sentAt) {
        long receivedAt = time.currentMillis();
        long skew = received.storeNow() - (sentAt + (receivedAt - sentAt) / 2);
        skewMillis = skew;
        if (Math.abs(skew) > tolerance.toMillis() / 2) {
            notify(l -> l.onClockSkew(namespace, skew));
        }
    }

    /** Publishes a new holding as a new binding; called with the lock held. */
    private void publish(AcquireResult.Acquired result) {
        SlotLease acquired = result.lease();
        final String reason = result.reason().label();
        SlotBinding previous = binding;
        if (previous != null) {
            previous.invalidate();
        }
        generation++;
        lease = acquired;
        binding = new SlotBinding(acquired.slot(), acquired.floor(), acquired.ceiling(), generation);
        log.info("Acquired slot {} of namespace [{}] ({}), window ({}, {}], fence {}", acquired.slot(), namespace, reason,
            acquired.floor(), acquired.ceiling(), acquired.fence());
        notify(l -> l.onSlotAcquired(acquired, reason));
        if (result.duplicateOwnerKey()) {
            log.warn("Another runtime with owner key [{}] holds a slot of namespace [{}]: check how the owner key is derived", owner.key(), namespace);
            notify(l -> l.onDuplicateOwnerKey(namespace, owner.key()));
        }
    }

    private void renewLoop() {
        if (Thread.currentThread() == starter) {
            ranInline = true;
            return;
        }
        lock.lock();
        try {
            while (state == State.RUNNING) {
                long waitNanos = runDueLocked();
                if (waitNanos > 0) {
                    wakeUp.awaitNanos(waitNanos);
                }
            }
        } catch (InterruptedException e) {
            if (state == State.RUNNING) {
                log.error("The renewals of namespace [{}] were interrupted; the lease will expire", namespace);
            }
            Thread.currentThread().interrupt();
        } catch (RuntimeException | Error e) {
            log.error("The renewals of namespace [{}] stopped; the lease will expire", namespace, e);
            throw e;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Renews or re-acquires if due; what the renewal task does on each wake-up.
     *
     * @return nanoseconds until the next due time, or -1 when the keeper does not run
     */
    long runDue() {
        lock.lock();
        try {
            return state == State.RUNNING ? runDueLocked() : -1;
        } finally {
            lock.unlock();
        }
    }

    private long runDueLocked() {
        long now = ticker.nanoTime();
        if (now < nextDueNanos) {
            return nextDueNanos - now;
        }
        SlotLease held = lease;
        if (held != null) {
            renew(held);
        } else {
            reacquire();
        }
        return Math.max(0, nextDueNanos - ticker.nanoTime());
    }

    private void renew(SlotLease held) {
        long sentAt = time.currentMillis();
        Optional<SlotLease> renewed;
        try {
            renewed = store.renew(held, ttl, tolerance);
        } catch (RuntimeException e) {
            backoffNanos = nextBackoff(backoffNanos);
            nextDueNanos = ticker.nanoTime() + backoffNanos;
            log.warn("Renewing slot {} of namespace [{}] failed; retrying in {}ms", held.slot(), namespace,
                TimeUnit.NANOSECONDS.toMillis(backoffNanos), e);
            notify(l -> l.onSlotRenewFailed(held, e));
            return;
        }
        backoffNanos = 0;
        if (renewed.isPresent()) {
            SlotLease fresh = renewed.get();
            observeClock(fresh, sentAt);
            lease = fresh;
            binding.raiseCeiling(fresh.ceiling());
            nextDueNanos = ticker.nanoTime() + renewIntervalNanos;
            notify(l -> l.onSlotRenewed(fresh));
            return;
        }
        // The owner or fence no longer match: another holder may already issue on the slot. Stop at once, then re-acquire.
        binding.invalidate();
        lease = null;
        log.warn("Lost slot {} of namespace [{}]; re-acquiring", held.slot(), namespace);
        notify(l -> l.onSlotLost(held));
        reacquire();
    }

    private void reacquire() {
        try {
            AcquireResult result = acquireOnce();
            backoffNanos = 0;
            if (result instanceof AcquireResult.Acquired acquired) {
                publish(acquired);
                nextDueNanos = ticker.nanoTime() + renewIntervalNanos;
            } else {
                long waitNanos = untilExpiry((AcquireResult.Exhausted) result);
                nextDueNanos = ticker.nanoTime() + waitNanos;
                log.warn("Namespace [{}] has no free slot; generators fail until one is acquired, retrying in {}ms", namespace,
                    TimeUnit.NANOSECONDS.toMillis(waitNanos));
            }
        } catch (RuntimeException e) {
            backoffNanos = nextBackoff(backoffNanos);
            nextDueNanos = ticker.nanoTime() + backoffNanos;
            log.warn("Re-acquiring a slot in namespace [{}] failed; retrying in {}ms", namespace, TimeUnit.NANOSECONDS.toMillis(backoffNanos), e);
        }
    }

    /** One retry on an error: a lost response makes the retry return false, which is fine, as the lease is gone. */
    private void releaseQuietly(SlotLease held, long lastIssued) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                if (store.release(held, lastIssued)) {
                    log.info("Released slot {} of namespace [{}]", held.slot(), namespace);
                    notify(l -> l.onSlotReleased(held));
                } else {
                    log.warn("Slot {} of namespace [{}] was no longer held at release", held.slot(), namespace);
                }
                return;
            } catch (RuntimeException e) {
                log.warn("Releasing slot {} of namespace [{}] failed; it expires instead", held.slot(), namespace, e);
            }
        }
    }

    private void notify(Consumer<GeneratorListener> event) {
        try {
            event.accept(listener);
        } catch (RuntimeException e) {
            log.warn("A listener failed on a lease event of namespace [{}]", namespace, e);
        }
    }

    private enum State {
        NEW,
        STARTING,
        RUNNING,
        CLOSED
    }
}
