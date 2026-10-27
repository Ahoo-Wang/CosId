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

package me.ahoo.cosid.internal;

import me.ahoo.cosid.ClockBackwardsException;
import me.ahoo.cosid.SlotLostException;
import me.ahoo.cosid.SlotWindowExceededException;
import me.ahoo.cosid.lease.SlotBinding;
import me.ahoo.cosid.lease.SlotHandle;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.time.ClockRegressionPolicy;
import me.ahoo.cosid.time.IdParts;
import me.ahoo.cosid.time.Layout;
import me.ahoo.cosid.time.TimeSource;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * The sequencer of a time-based generator (03-core-model.md §4.1, 05-machine-lease.md §4.1).
 *
 * <p>The state of a binding is one {@code long}, {@code time << seqBits | seq} of the last issued id, next to that
 * binding ({@link Bound}). A new binding of the slot swaps in a new {@code Bound}, which resets the sequence; the
 * replaced state is retired to {@link #CLOSED}. {@link #close()} swaps in a terminal {@code Bound} and retires the
 * last state, which gives the exact last issued time.
 *
 * <p>Issuing is a short critical section: read the binding and the time, check the window, compute and publish the
 * state. Nothing waits inside it (01 §2.1): an attempt that has to wait (sequence exhausted, clock regression, slot
 * floor, renewal at the ceiling) returns, the lock is released, the thread waits, then retries. Listener calls happen
 * outside the lock too. A lock beats a CAS loop here: under contention the lock keeps the state in one core's cache
 * while it issues, where every lost CAS moves it (benchmark P1, 16 threads).
 */
public final class TimeSequencer {
    /** The state of a retired or closed binding. */
    static final long CLOSED = Long.MIN_VALUE;
    /** The state of a binding that issued nothing yet. */
    static final long INITIAL = -1L;
    /** {@link #attempt()}: wait {@link #outcomeMillis}, then retry. */
    private static final long WAIT = -1L;
    /** {@link #attempt()}: the sequence of the unit is used up; wait {@link #outcomeMillis}, then retry. */
    private static final long EXHAUSTED = -2L;
    /** {@link #attempt()}: the clock went back by {@link #outcomeDetail}; wait {@link #outcomeMillis}, then retry. */
    private static final long REGRESSION_WAIT = -3L;
    /** {@link #attempt()}: the clock went back by {@link #outcomeDetail}, beyond {@code waitUpTo}. */
    private static final long REGRESSION_FAIL = -4L;
    /** {@link #attempt()}: timestamp {@link #outcomeMillis} is beyond the ceiling {@link #outcomeDetail}. */
    private static final long WINDOW_EXCEEDED = -5L;
    private static final long MAX_PARK_MILLIS = 10;

    private static final Bound TERMINATED = new Bound(new SlotBinding(0, 0, 0, -1), new AtomicLong(CLOSED));

    private final String name;
    private final Layout layout;
    private final SlotHandle slot;
    private final TimeSource time;
    private final GeneratorListener listener;

    private final long epochMillis;
    private final long unitMillis;
    private final int seqBits;
    private final long seqMax;
    private final long seqResetThreshold;
    private final int timeShift;
    private final long maxTime;
    private final int maxNode;
    private final long borrowUpToMillis;
    private final long waitUpToMillis;
    private final long waitUpToNanos;

    /** Guards each attempt: reading the time, the window checks, computing and publishing the state. No wait inside. */
    private final Object lock = new Object();
    /** Swapped under {@link #lock}; read without it by {@link #lastIssuedMillis()}. */
    private final AtomicReference<Bound> bound;
    /** The largest time issued on a retired binding, in units; -1 when none. */
    private final AtomicLong retiredTime = new AtomicLong(-1);

    /** Under {@link #lock}: the slot of the state an attempt published. */
    private int issuedSlot;
    /** Under {@link #lock}: the first detail of an attempt's outcome (milliseconds to wait, or the window timestamp). */
    private long outcomeMillis;
    /** Under {@link #lock}: the second detail (how far the clock went back, -1 for none; or the window ceiling). */
    private long outcomeDetail;

    /**
     * A sequencer.
     *
     * @param name       the generator name
     * @param layout     the id layout
     * @param slot       the machine slot
     * @param time       the time source
     * @param regression the clock regression policy
     * @param listener   the event listener
     */
    public TimeSequencer(String name, Layout layout, SlotHandle slot, TimeSource time, ClockRegressionPolicy regression,
                         GeneratorListener listener) {
        this.name = name;
        this.layout = layout;
        this.slot = slot;
        this.time = time;
        this.listener = listener;
        this.epochMillis = layout.epoch().toEpochMilli();
        this.unitMillis = layout.unit().getDuration().toMillis();
        this.seqBits = layout.seqBits();
        this.seqMax = ~(-1L << seqBits);
        this.seqResetThreshold = layout.seqResetThreshold();
        this.timeShift = layout.nodeBits() + seqBits;
        // The state keeps the time above the sequence in a non-negative long.
        this.maxTime = Math.min(~(-1L << layout.timeBits()), Long.MAX_VALUE >>> seqBits);
        this.maxNode = ~(-1 << layout.nodeBits());
        this.borrowUpToMillis = saturatedMillis(regression.borrowUpTo());
        this.waitUpToMillis = saturatedMillis(regression.waitUpTo());
        this.waitUpToNanos = waitUpToMillis >= Long.MAX_VALUE / 1_000_000 ? Long.MAX_VALUE : regression.waitUpTo().toNanos();
        this.bound = new AtomicReference<>(new Bound(null, new AtomicLong(CLOSED)));
    }

    /**
     * Issues the next id; the layout must fit a {@code long}.
     *
     * @return the id
     */
    public long nextId() {
        long waitStarted = 0;
        for (; ; ) {
            long next;
            int nextSlot;
            long millis;
            long detail;
            synchronized (lock) {
                next = attempt();
                nextSlot = issuedSlot;
                millis = outcomeMillis;
                detail = outcomeDetail;
            }
            if (next >= 0) {
                if (detail >= 0) {
                    listener.onClockRegression(name, detail, "borrow");
                }
                return (next >>> seqBits) << timeShift | (long) nextSlot << seqBits | next & seqMax;
            }
            waitStarted = outsideLock(next, millis, detail, waitStarted);
        }
    }

    /**
     * Issues the next id as parts.
     *
     * @return the parts
     */
    public IdParts nextParts() {
        long waitStarted = 0;
        for (; ; ) {
            long next;
            int nextSlot;
            long millis;
            long detail;
            synchronized (lock) {
                next = attempt();
                nextSlot = issuedSlot;
                millis = outcomeMillis;
                detail = outcomeDetail;
            }
            if (next >= 0) {
                if (detail >= 0) {
                    listener.onClockRegression(name, detail, "borrow");
                }
                return new IdParts(next >>> seqBits, nextSlot, next & seqMax);
            }
            waitStarted = outsideLock(next, millis, detail, waitStarted);
        }
    }

    /**
     * Handles an attempt that did not issue, with the lock released: tells the listener, waits or throws.
     *
     * @return when the bounded wait for a renewal started, {@link System#nanoTime()} with the low bit set, or 0
     */
    private long outsideLock(long outcome, long millis, long detail, long waitStarted) {
        if (outcome == EXHAUSTED) {
            listener.onSequenceExhausted(name);
        } else if (outcome == REGRESSION_WAIT) {
            listener.onClockRegression(name, detail, "wait");
        } else if (outcome == REGRESSION_FAIL) {
            listener.onClockRegression(name, detail, "fail");
            throw new ClockBackwardsException(name, detail);
        } else if (outcome == WINDOW_EXCEEDED) {
            if (waitStarted == 0) {
                listener.onWindowExceeded(name);
            }
            if (waitStarted == 0 ? waitUpToNanos == 0 : System.nanoTime() - waitStarted >= waitUpToNanos) {
                throw new SlotWindowExceededException(name, millis, detail);
            }
            pause(MAX_PARK_MILLIS);
            return waitStarted == 0 ? System.nanoTime() | 1 : waitStarted;
        }
        pause(millis);
        return waitStarted;
    }

    /**
     * The slot of the current binding.
     *
     * @return the slot
     */
    public int slot() {
        return slot.current().slot();
    }

    /**
     * The time of the last issued id.
     *
     * @return milliseconds since the Unix epoch, 0 when none was issued; exact once {@link #close()} returned
     */
    public long lastIssuedMillis() {
        long last = retiredTime.get();
        long state = bound.get().state.get();
        if (state >= 0) {
            last = Math.max(last, state >>> seqBits);
        }
        return last < 0 ? 0 : epochMillis + last * unitMillis;
    }

    /**
     * Moves the sequencer to its terminal state: no id is issued once this returns.
     */
    public void close() {
        synchronized (lock) {
            Bound current = bound.get();
            if (current != TERMINATED) {
                bound.set(TERMINATED);
                retire(current);
            }
        }
    }

    /**
     * Under {@link #lock}: the bound of the current binding, a new one with a reset sequence when the slot has a new
     * binding.
     */
    private Bound bound() {
        Bound current = bound.get();
        SlotBinding binding = slot.current();
        if (current.binding == binding) {
            return current;
        }
        if (current == TERMINATED) {
            throw closed();
        }
        if (binding.slot() > maxNode) {
            throw new IllegalStateException("Slot " + binding.slot() + " does not fit the " + layout.nodeBits()
                + " node bits of generator [" + name + "]");
        }
        Bound fresh = new Bound(binding, new AtomicLong(INITIAL));
        bound.set(fresh);
        retire(current);
        return fresh;
    }

    private void retire(Bound retired) {
        long state = retired.state.getAndSet(CLOSED);
        if (state >= 0) {
            retiredTime.accumulateAndGet(state >>> seqBits, Math::max);
        }
    }

    /**
     * Under {@link #lock}: one attempt. Never waits; an outcome that needs a wait or an event leaves its details in
     * {@link #outcomeMillis} and {@link #outcomeDetail}.
     *
     * @return the published state {@code time << seqBits | seq}, its slot in {@link #issuedSlot} and a borrowed
     *     regression in {@link #outcomeDetail} (else -1); or a negative outcome
     */
    private long attempt() {
        Bound current = bound();
        SlotBinding binding = current.binding;
        if (!binding.isValid()) {
            throw new SlotLostException(name, binding.slot());
        }
        long state = current.state.get();
        long nowMillis = time.currentMillis();
        long now = toUnits(nowMillis);
        long use;
        long seq;
        long borrowed = -1;
        if (state == INITIAL) {
            use = now;
            seq = 0;
        } else {
            long last = state >>> seqBits;
            long lastSeq = state & seqMax;
            long needed = lastSeq < seqMax ? last : last + 1;
            if (now > last) {
                use = now;
                seq = nextUnitSeq(lastSeq);
            } else if (now == last && lastSeq < seqMax) {
                use = now;
                seq = lastSeq + 1;
            } else if (now == last) {
                return outcome(EXHAUSTED, toMillis(needed) - nowMillis, -1);
            } else {
                long backwards = toMillis(last) - nowMillis;
                long ahead = toMillis(needed) - nowMillis;
                if (ahead > waitUpToMillis && ahead > borrowUpToMillis) {
                    return outcome(REGRESSION_FAIL, 0, backwards);
                }
                if (ahead > borrowUpToMillis) {
                    return outcome(REGRESSION_WAIT, ahead, backwards);
                }
                use = needed;
                seq = needed == last ? lastSeq + 1 : nextUnitSeq(lastSeq);
                borrowed = backwards;
            }
        }
        if (Long.compareUnsigned(use, maxTime) > 0) {
            throw use < 0
                ? new IllegalStateException("The epoch " + layout.epoch() + " of generator [" + name + "] is in the future")
                : new IllegalStateException("The time " + use + " overflows the " + layout.timeBits() + " time bits of generator [" + name + "]");
        }
        long useMillis = toMillis(use);
        if (useMillis <= binding.floor()) {
            // The first unit that starts above the floor.
            return outcome(WAIT, toMillis(toUnits(binding.floor()) + 1) - nowMillis, -1);
        }
        long ceiling = binding.ceiling();
        if (useMillis + unitMillis - 1 > ceiling) {
            return outcome(WINDOW_EXCEEDED, useMillis, ceiling);
        }
        long next = use << seqBits | seq;
        current.state.set(next);
        issuedSlot = binding.slot();
        outcomeDetail = borrowed;
        return next;
    }

    private long outcome(long outcome, long millis, long detail) {
        outcomeMillis = millis;
        outcomeDetail = detail;
        return outcome;
    }

    /**
     * The first sequence of a new time unit: the next one, or 0 past the reset threshold (the low-bit scattering of
     * 4.x: ids of a lightly used generator do not all end in the same low bits).
     */
    private long nextUnitSeq(long lastSeq) {
        return lastSeq >= seqResetThreshold ? 0 : lastSeq + 1;
    }

    private long toUnits(long millis) {
        long sinceEpoch = millis - epochMillis;
        return unitMillis == 1 ? sinceEpoch : Math.floorDiv(sinceEpoch, unitMillis);
    }

    private long toMillis(long units) {
        return epochMillis + units * unitMillis;
    }

    private static long saturatedMillis(Duration duration) {
        try {
            return duration.toMillis();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private IllegalStateException closed() {
        return new IllegalStateException("Generator [" + name + "] is closed");
    }

    /**
     * Waits about {@code millis} without holding anything: spins for the last millisecond, parks otherwise.
     */
    private static void pause(long millis) {
        if (millis <= 1) {
            Thread.onSpinWait();
        } else {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(Math.min(millis - 1, MAX_PARK_MILLIS)));
        }
    }

    /**
     * A binding and the sequencer state of this generator on it.
     *
     * @param binding the binding
     * @param state   {@code time << seqBits | seq} of the last issued id, {@link #INITIAL} or {@link #CLOSED}
     */
    record Bound(SlotBinding binding, AtomicLong state) {
    }
}
