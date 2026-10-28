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

package me.ahoo.cosid.segment;

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.RangeExhaustedException;
import me.ahoo.cosid.RangeSealedException;
import me.ahoo.cosid.RangeUnavailableException;
import me.ahoo.cosid.internal.BatchSizer;
import me.ahoo.cosid.internal.ExpiringRangeCursor;
import me.ahoo.cosid.internal.RangeCursor;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.time.TimeSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Issues the values of ranges reserved from a {@link RangeStore} (04-segment.md §2): one atomic increment on the hot
 * path, refills off the lock and single-flight, reservation size adapted to a time budget.
 *
 * <p>The hot path reads the current cursor once and takes a value from it. Only when the cursor is used up, expired or
 * closed does a caller take the switch lock (spinning briefly first, since another caller usually switches within
 * microseconds), move to the next ready range, and start a refill when the ready values fall below the lookahead
 * budget. The switch lock never covers I/O: reservations run on the refill executor, single-flight, and a caller with
 * no value left waits for the one in flight outside the lock, at most {@link BatchPolicy#reserveTimeout()}, or makes
 * it itself (still outside the lock) if the executor has not started it. A reservation that completes after its
 * waiters gave up still delivers its range. After a failed reservation no new one starts for a backoff that doubles
 * from 100ms up to 5s; meanwhile callers with no value left fail at once.
 */
public final class SegmentGenerator implements IdGenerator, AutoCloseable {
    static final long INITIAL_BACKOFF_MILLIS = 100;
    static final long MAX_BACKOFF_MILLIS = 5_000;
    /**
     * The ready queue holds at most this many batches: enough runway that a refill thread descheduled for a few
     * milliseconds under full load leaves no caller waiting, bounded when the budget is far above {@code maxBatch}.
     */
    static final long MAX_READY_RANGES = 64;
    /** How long a caller spins for another caller's range switch before it parks on the lock. */
    private static final int SWITCH_SPINS = 1 << 12;
    /** The terminal sentinel: it never issues, so the hot path needs no closed check. */
    private static final RangeCursor CLOSED = new RangeCursor(0, Long.MIN_VALUE);
    private static final Logger log = LoggerFactory.getLogger(SegmentGenerator.class);
    /**
     * The operation {@link GeneratorListener#onStoreTimeout} reports when callers waited for a refill longer than the
     * reserve timeout; a reserve call that hits the store's own timeout is reported as {@code reserve}.
     */
    public static final String RESERVE_WAIT = "reserve-wait";

    private final String name;
    private final String key;
    private final String storeName;
    private final RangeStore store;
    private final BatchPolicy policy;
    private final Executor refillExecutor;
    private final TimeSource time;
    private final GeneratorListener listener;
    private final boolean prefetching;
    /**
     * Whether the switch lock reads the clock on every switch: for range expiry and for the rate estimate, which only
     * a lookahead uses. Without either (P6: step 1, no lookahead) it reads the clock only around a failure backoff.
     */
    private final boolean clocked;
    private final long rangeTtlMillis;
    private final long reserveTimeoutNanos;
    private final ReentrantLock switchLock = new ReentrantLock();

    /** Installed while no range is at hand, so that a used-up cursor is not incremented any further. */
    private final RangeCursor empty = new RangeCursor(0, 0);
    private volatile RangeCursor current = empty;

    // Guarded by switchLock.
    private final ArrayDeque<RangeCursor> ready = new ArrayDeque<>();
    private final BatchSizer sizer;
    private long readyValues;
    /** A reservation is in flight (single flight), the one numbered {@code refillSeq}. */
    private boolean refilling;
    private long refillSeq;
    private long refillSize;
    /** Whether the executor or a waiting caller makes it. */
    private boolean refillClaimed;
    /** Created by its first waiter, completed once its range is ready. */
    private CompletableFuture<Void> refillDone;
    /** The refill whose wait timed out last: a refill reports one wait timeout, however many callers waited. */
    private final AtomicReference<CompletableFuture<Void>> timedOut = new AtomicReference<>();
    private long currentSize;
    private long currentSinceMillis;
    private long highWater = -1;
    private long backoffMillis;
    private long retryAfterMillis;
    private Throwable lastFailure;

    /**
     * A segment generator. It reserves nothing until the first {@link #generate()}.
     *
     * @param name           the generator name, for events and errors
     * @param key            the range key
     * @param store          the range store
     * @param policy         the batch policy
     * @param refillExecutor runs the reservations; the runtime's own executor by default
     * @param time           the time source
     * @param listener       the event listener
     */
    public SegmentGenerator(String name, String key, RangeStore store, BatchPolicy policy,
                            Executor refillExecutor, TimeSource time, GeneratorListener listener) {
        this(name, key, "", store, policy, refillExecutor, time, listener);
    }

    /**
     * A segment generator on a named store. It reserves nothing until the first {@link #generate()}.
     *
     * @param name           the generator name, for events and errors
     * @param key            the range key
     * @param storeName      the store name, for {@link GeneratorListener#onStoreTimeout} when a caller waits for a
     *                       refill longer than the reserve timeout
     * @param store          the range store
     * @param policy         the batch policy
     * @param refillExecutor runs the reservations; the runtime's own executor by default
     * @param time           the time source
     * @param listener       the event listener
     */
    public SegmentGenerator(String name, String key, String storeName, RangeStore store, BatchPolicy policy,
                            Executor refillExecutor, TimeSource time, GeneratorListener listener) {
        this.name = Objects.requireNonNull(name, "name");
        this.key = Objects.requireNonNull(key, "key");
        this.storeName = Objects.requireNonNull(storeName, "storeName");
        this.store = Objects.requireNonNull(store, "store");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.refillExecutor = Objects.requireNonNull(refillExecutor, "refillExecutor");
        this.time = Objects.requireNonNull(time, "time");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.prefetching = !policy.lookahead().isZero();
        this.rangeTtlMillis = policy.rangeTtl().map(Duration::toMillis).orElse(0L);
        this.reserveTimeoutNanos = policy.reserveTimeout().toNanos();
        this.clocked = prefetching || rangeTtlMillis > 0;
        this.sizer = new BatchSizer(policy.step(), policy.maxBatch(), policy.lookahead());
    }

    public String name() {
        return name;
    }

    public String key() {
        return key;
    }

    public BatchPolicy policy() {
        return policy;
    }

    /**
     * Issues the next value.
     *
     * @return the value
     * @throws me.ahoo.cosid.RangeUnavailableException if no range arrives within the reserve timeout, or a refill is
     *                                                 backing off after a failure
     * @throws me.ahoo.cosid.RangeSealedException      if the key is sealed
     * @throws me.ahoo.cosid.RangeExhaustedException   if the key has no values left
     * @throws IllegalStateException                   if the generator is closed
     */
    @Override
    public long generate() {
        RangeCursor cursor = current;
        long value = cursor.take();
        if (value >= 0) {
            return value;
        }
        return slowPath(cursor);
    }

    private long slowPath(RangeCursor seen) {
        RangeCursor cursor = seen;
        long deadline = 0;
        while (true) {
            long start;
            long reserveHereSize = 0;
            CompletableFuture<Void> wait = null;
            long resizedFrom = -1;
            long resizedTo = 0;
            if (!lockOrSwitched(cursor)) {
                // Another caller switched meanwhile: take from the new range without the lock.
                cursor = current;
                long value = cursor.take();
                if (value >= 0) {
                    return value;
                }
                continue;
            }
            try {
                if (current == CLOSED) {
                    throw closed();
                }
                long now = nowLocked();
                RangeCursor at = current;
                if (at == cursor || !at.usable(now)) {
                    resizedFrom = advanceLocked(at, now);
                    resizedTo = sizer.batch();
                }
                start = maybeRefillLocked(now);
                // The hot path takes from the current cursor outside the lock, so it may have run out since it was
                // checked above: ready ranges then come first, on the next turn of the loop. Only with none left does
                // a caller wait for the reservation in flight or make it itself; one it made would otherwise issue
                // its first value ahead of the ready ranges, which close() would then drop.
                if (!current.usable(now) && ready.isEmpty()) {
                    if (refilling && !refillClaimed) {
                        // A caller that would only wait for a reservation the executor has not started makes it
                        // itself, still outside the lock: no hand-off when nothing is prefetched (P6), and no wait
                        // on an executor starved by busy callers (D21).
                        refillClaimed = true;
                        start = refillSeq;
                        reserveHereSize = refillSize;
                    } else if (refilling) {
                        if (refillDone == null) {
                            refillDone = new CompletableFuture<>();
                        }
                        wait = refillDone;
                    } else if (lastFailure != null) {
                        throw unavailable(lastFailure);
                    }
                    // Otherwise other threads used up the range just installed: take again.
                }
            } finally {
                switchLock.unlock();
            }
            if (resizedFrom >= 0) {
                fireResized(resizedFrom, resizedTo);
            }
            if (reserveHereSize > 0) {
                long first = reserve(start, reserveHereSize, true);
                if (first >= 0) {
                    return first;
                }
            } else if (start != 0) {
                submit(start);
            }
            if (wait != null) {
                if (deadline == 0) {
                    deadline = System.nanoTime() + reserveTimeoutNanos;
                }
                await(wait, deadline);
            }
            cursor = current;
            long value = cursor.take();
            if (value >= 0) {
                return value;
            }
        }
    }

    /**
     * Takes the switch lock, unless the current cursor moves past {@code cursor} first. When a range runs out, every
     * caller lands here at once; the one holding the lock switches within a few microseconds (it never does I/O), so
     * the others spin briefly for the switch instead of parking one after the other.
     *
     * @return true with the lock held, false (without it) if the cursor was switched meanwhile
     */
    private boolean lockOrSwitched(RangeCursor cursor) {
        if (switchLock.tryLock()) {
            return true;
        }
        for (int spin = 0; spin < SWITCH_SPINS; spin++) {
            if (current != cursor) {
                return false;
            }
            Thread.onSpinWait();
            if (switchLock.tryLock()) {
                return true;
            }
        }
        switchLock.lock();
        return true;
    }

    /**
     * Under the switch lock: the current time, or 0 when nothing reads it (see {@link #clocked}).
     */
    private long nowLocked() {
        return clocked || retryAfterMillis != 0 ? time.currentMillis() : 0;
    }

    /**
     * Moves from {@code used} (the current cursor, used up or expired) to the next ready range that has not expired,
     * or to an empty cursor, so that the used-up cursor is not incremented any further. A range used up (not expired)
     * feeds the rate estimate.
     *
     * @return the previous batch size if the estimate changed it, otherwise {@code -1}
     */
    private long advanceLocked(RangeCursor used, long now) {
        long resizedFrom = -1;
        // Without a lookahead the estimate never changes the batch (it stays at step): no sample is taken.
        if (prefetching && currentSize > 0 && !(used instanceof ExpiringRangeCursor expiring && now >= expiring.expiresAtMillis())) {
            resizedFrom = sizer.record(currentSize, now - currentSinceMillis);
        }
        currentSize = 0;
        while (!ready.isEmpty()) {
            RangeCursor next = ready.poll();
            long size = next.untaken();
            readyValues -= size;
            if (next.remaining(now) == 0) {
                continue;
            }
            current = next;
            currentSize = size;
            currentSinceMillis = now;
            return resizedFrom;
        }
        current = empty;
        return resizedFrom;
    }

    /**
     * Starts a refill when none is in flight and no backoff is pending, and the ready ranges hold less than the
     * lookahead budget (the estimated rate times {@code lookahead}), at least a batch and at most
     * {@value #MAX_READY_RANGES} batches; without prefetching, when no value is left at all. The caller submits it
     * after unlocking.
     */
    private long maybeRefillLocked(long now) {
        if (refilling || current == CLOSED || now < retryAfterMillis) {
            return 0;
        }
        long batch = sizer.batch();
        // Saturating: a batch near Long.MAX_VALUE must not wrap the target negative, or no refill would ever start.
        long readyCap = batch > Long.MAX_VALUE / MAX_READY_RANGES ? Long.MAX_VALUE : batch * MAX_READY_RANGES;
        if (prefetching ? readyValues >= Math.min(Math.max(batch, sizer.budget()), readyCap)
            : current.remaining(now) + readyValues > 0) {
            return 0;
        }
        refilling = true;
        refillClaimed = false;
        refillSize = batch;
        return ++refillSeq;
    }

    private void submit(long seq) {
        try {
            refillExecutor.execute(() -> {
                long size = claim(seq);
                if (size > 0) {
                    reserve(seq, size, false);
                }
            });
        } catch (RuntimeException rejected) {
            if (claim(seq) > 0) {
                complete(seq, 0, 0, rejected, 0, false);
            }
        }
    }

    /**
     * Claims refill {@code seq} for the executor.
     *
     * @return its size, or {@code 0} if a waiting caller already makes it (or it was cancelled)
     */
    private long claim(long seq) {
        switchLock.lock();
        try {
            if (!refilling || refillSeq != seq || refillClaimed) {
                return 0;
            }
            refillClaimed = true;
            return refillSize;
        } finally {
            switchLock.unlock();
        }
    }

    /**
     * Makes refill {@code seq}. A caller that makes it itself ({@code here}) takes the first value of the range
     * directly (R1-core-04): with step 1 the range is that one value, and no cursor is allocated or installed for it.
     *
     * @param here whether a caller with no value left makes it, rather than the executor
     * @return the first value of the range, taken by the caller; {@code -1} if none was taken
     * @throws RuntimeException {@code here}: if the refill failed, as {@link #generate()} reports it
     */
    private long reserve(long seq, long size, boolean here) {
        long started = 0;
        long start = 0;
        long end = 0;
        Throwable error = null;
        if (current == CLOSED) {
            error = closed();
        } else {
            started = listener == GeneratorListener.NOOP ? 0 : System.nanoTime();
            try {
                // Only the bounds are kept: the Range itself does not escape, so it need not be allocated.
                Range range = Objects.requireNonNull(store.reserve(key, size, policy.initialOffset()), "reserved range");
                start = range.start();
                end = range.end();
            } catch (Throwable e) {
                error = e;
            }
        }
        boolean take = here && error == null && start < RangeCursor.cap(end);
        Throwable outcome = complete(seq, start, end, error, started == 0 ? 0 : System.nanoTime() - started, take);
        if (error instanceof Error fatal) {
            throw fatal;
        }
        if (outcome != null) {
            if (here) {
                throw current == CLOSED ? closed() : unavailable(outcome);
            }
            return -1;
        }
        return take ? start : -1;
    }

    /**
     * Records the outcome of refill {@code seq}: the range goes to the ready queue, or the failure starts a backoff.
     *
     * @param takeFirst whether the caller takes {@code rangeStart} itself, so that only the rest is queued
     * @return null if the range was delivered, otherwise the failure (or why it was dropped)
     */
    private Throwable complete(long seq, long rangeStart, long rangeEnd, Throwable failure, long latencyNanos, boolean takeFirst) {
        Throwable error = failure;
        long chained = 0;
        boolean delivered = false;
        long backoff = 0;
        CompletableFuture<Void> done = null;
        switchLock.lock();
        try {
            if (refilling && refillSeq == seq) {
                refilling = false;
                done = refillDone;
                refillDone = null;
            }
            if (current != CLOSED) {
                if (error == null && rangeStart <= highWater) {
                    error = new IllegalStateException("Range store returned [" + rangeStart + ", " + rangeEnd
                        + ") for key [" + key + "], overlapping the ranges before it, which end at " + highWater);
                }
                long now = error != null ? time.currentMillis() : nowLocked();
                if (error == null) {
                    delivered = true;
                    highWater = rangeEnd - 1;
                    backoffMillis = 0;
                    retryAfterMillis = 0;
                    lastFailure = null;
                    long end = RangeCursor.cap(rangeEnd);
                    long first = takeFirst ? rangeStart + 1 : rangeStart;
                    RangeCursor rest = null;
                    if (first < end) {
                        rest = rangeTtlMillis > 0
                            ? new ExpiringRangeCursor(first, end, saturatedAdd(now, rangeTtlMillis), time)
                            : new RangeCursor(first, end);
                        ready.add(rest);
                        readyValues += end - first;
                    }
                    if (current == empty) {
                        advanceLocked(current, now);
                    }
                    if (takeFirst) {
                        // The value the caller takes counts for the rate estimate as part of its range.
                        if (rest != null && current == rest) {
                            currentSize++;
                        } else if (current == empty) {
                            currentSize = 1;
                            currentSinceMillis = now;
                        }
                    }
                    // Without a lookahead a refill starts when no value is left; the caller holds the one it took.
                    chained = takeFirst && !prefetching ? 0 : maybeRefillLocked(now);
                } else {
                    backoffMillis = backoffMillis == 0 ? INITIAL_BACKOFF_MILLIS : Math.min(MAX_BACKOFF_MILLIS, backoffMillis * 2);
                    retryAfterMillis = saturatedAdd(now, backoffMillis);
                    lastFailure = error;
                    backoff = backoffMillis;
                }
            }
        } finally {
            switchLock.unlock();
        }
        Throwable outcome = null;
        if (delivered) {
            fireReserved(rangeStart, rangeEnd, latencyNanos);
            if (done != null) {
                done.complete(null);
            }
        } else {
            if (error != null && current != CLOSED) {
                log.warn("Generator [{}] - refill of key [{}] failed, retrying after {}ms: {}", name, key, backoff, error.toString());
                fireFailed(error);
            }
            outcome = error != null ? error : closed();
            if (done != null) {
                done.completeExceptionally(outcome);
            }
        }
        if (chained != 0) {
            submit(chained);
        }
        return outcome;
    }

    private void await(CompletableFuture<Void> done, long deadline) {
        try {
            done.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            if (current == CLOSED) {
                throw closed();
            }
            if (timedOut.getAndSet(done) != done) {
                fireWaitTimeout();
            }
            throw new RangeUnavailableException(key, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RangeUnavailableException(key, e);
        } catch (ExecutionException e) {
            if (current == CLOSED) {
                throw closed();
            }
            throw unavailable(e.getCause());
        }
    }

    private RuntimeException unavailable(Throwable cause) {
        if (cause instanceof RangeSealedException) {
            return new RangeSealedException(key);
        }
        if (cause instanceof RangeExhaustedException) {
            return new RangeExhaustedException(key);
        }
        return new RangeUnavailableException(key, cause);
    }

    private IllegalStateException closed() {
        return new IllegalStateException("Segment generator [" + name + "] is closed");
    }

    private void fireReserved(long start, long end, long latencyNanos) {
        if (listener == GeneratorListener.NOOP) {
            return;
        }
        try {
            listener.onRangeReserved(name, key, new Range(start, end), Duration.ofNanos(latencyNanos));
        } catch (RuntimeException e) {
            listenerFailed(e);
        }
    }

    private void fireFailed(Throwable error) {
        try {
            listener.onRefillFailed(name, key, error);
        } catch (RuntimeException e) {
            listenerFailed(e);
        }
    }

    private void fireWaitTimeout() {
        try {
            listener.onStoreTimeout(storeName, RESERVE_WAIT);
        } catch (RuntimeException e) {
            listenerFailed(e);
        }
    }

    private void fireResized(long from, long to) {
        try {
            listener.onBatchResized(name, from, to);
        } catch (RuntimeException e) {
            listenerFailed(e);
        }
    }

    private void listenerFailed(RuntimeException e) {
        log.warn("Generator [{}] - listener failed: {}", name, e.toString());
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }

    /**
     * Starts a refill if the values at hand are below the batch size, without waiting for it: the periodic generator
     * warms up the next period with it.
     */
    void prefetch() {
        long start;
        switchLock.lock();
        try {
            start = maybeRefillLocked(time.currentMillis());
        } finally {
            switchLock.unlock();
        }
        if (start != 0) {
            submit(start);
        }
    }

    boolean isClosed() {
        return current == CLOSED;
    }

    boolean switchLockHeldByCurrentThread() {
        return switchLock.isHeldByCurrentThread();
    }

    /**
     * The values at hand, for {@code describe()}.
     *
     * @return the rest of the current range plus the ready ranges
     */
    public long held() {
        switchLock.lock();
        try {
            return current.remaining(time.currentMillis()) + readyValues;
        } finally {
            switchLock.unlock();
        }
    }

    /**
     * The size of the next reservation, for {@code describe()}.
     *
     * @return the batch size the rate estimate gives now
     */
    public long batch() {
        switchLock.lock();
        try {
            return sizer.batch();
        } finally {
            switchLock.unlock();
        }
    }

    /**
     * Stops issuing, cancels the refill in flight and drops the ready ranges (S4 allows the gap). Closing again does
     * nothing.
     */
    @Override
    public void close() {
        CompletableFuture<Void> cancelled = null;
        switchLock.lock();
        try {
            if (current == CLOSED) {
                return;
            }
            current = CLOSED;
            ready.clear();
            readyValues = 0;
            if (refilling) {
                cancelled = refillDone;
                refilling = false;
                refillDone = null;
            }
        } finally {
            switchLock.unlock();
        }
        if (cancelled != null) {
            cancelled.completeExceptionally(closed());
        }
    }
}
