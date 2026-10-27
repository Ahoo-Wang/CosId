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
 * The lock-free sequencer of a time-based generator (03-core-model.md §4.1, 05-machine-lease.md §4.1).
 *
 * <p>The state of a binding is one {@code long}, {@code time << seqBits | seq} of the last issued id, in an
 * {@link AtomicLong} next to that binding ({@link Bound}). A new binding of the slot swaps in a new {@code Bound} with one
 * CAS, so only one of the threads that see the new binding resets the sequence; the replaced state is retired to
 * {@link #CLOSED}, so a thread still holding it retries on the new one. {@link #close()} swaps in a terminal
 * {@code Bound} and retires the last state, which gives the exact last issued time.
 *
 * <p>Waits (sequence exhausted, clock regression, slot floor, renewal at the ceiling) spin on
 * {@link Thread#onSpinWait()} for the last millisecond and park otherwise; nothing is held while waiting.
 */
public final class TimeSequencer {
    /** The state of a retired or closed binding. */
    static final long CLOSED = Long.MIN_VALUE;
    /** The state of a binding that issued nothing yet. */
    static final long INITIAL = -1L;
    /** {@link #tryNext(Bound, long)}: retry at once. */
    private static final long RETRY = -1L;
    /** {@link #tryNext(Bound, long)}: retry; a wait bounded by {@code waitUpTo} started. */
    private static final long RETRY_BOUNDED = -2L;
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

    private final AtomicReference<Bound> bound;
    /** The largest time issued on a retired binding, in units; -1 when none. */
    private final AtomicLong retiredTime = new AtomicLong(-1);
    private volatile boolean terminated;

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
            Bound current = bound();
            long next = tryNext(current, waitStarted);
            if (next >= 0) {
                return (next >>> seqBits) << timeShift | (long) current.binding.slot() << seqBits | next & seqMax;
            }
            if (next == RETRY_BOUNDED && waitStarted == 0) {
                waitStarted = System.nanoTime() | 1;
            }
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
            Bound current = bound();
            long next = tryNext(current, waitStarted);
            if (next >= 0) {
                return new IdParts(next >>> seqBits, current.binding.slot(), next & seqMax);
            }
            if (next == RETRY_BOUNDED && waitStarted == 0) {
                waitStarted = System.nanoTime() | 1;
            }
        }
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
        for (; ; ) {
            Bound current = bound.get();
            if (current == TERMINATED) {
                while (!terminated) {
                    Thread.onSpinWait();
                }
                return;
            }
            if (bound.compareAndSet(current, TERMINATED)) {
                retire(current);
                terminated = true;
                return;
            }
        }
    }

    /**
     * The bound of the current binding: swaps in a new one, with a reset sequence, when the slot has a new binding.
     */
    private Bound bound() {
        for (; ; ) {
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
            if (bound.compareAndSet(current, new Bound(binding, new AtomicLong(INITIAL)))) {
                retire(current);
            }
        }
    }

    private void retire(Bound retired) {
        long state = retired.state.getAndSet(CLOSED);
        if (state >= 0) {
            retiredTime.accumulateAndGet(state >>> seqBits, Math::max);
        }
    }

    /**
     * One attempt on {@code current}.
     *
     * @return the new state {@code time << seqBits | seq} once its CAS succeeded, else {@link #RETRY} or
     *     {@link #RETRY_BOUNDED}
     */
    private long tryNext(Bound current, long waitStarted) {
        SlotBinding binding = current.binding;
        long state = current.state.get();
        if (state == CLOSED) {
            if (bound.get() == TERMINATED) {
                throw closed();
            }
            return RETRY;
        }
        if (!binding.isValid()) {
            throw new SlotLostException(name, binding.slot());
        }
        long nowMillis = time.currentMillis();
        long now = toUnits(nowMillis);
        long use;
        long seq;
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
                listener.onSequenceExhausted(name);
                pause(toMillis(needed) - nowMillis);
                return RETRY;
            } else {
                long backwards = toMillis(last) - nowMillis;
                long ahead = toMillis(needed) - nowMillis;
                if (ahead <= borrowUpToMillis) {
                    listener.onClockRegression(name, backwards, "borrow");
                    use = needed;
                    seq = needed == last ? lastSeq + 1 : nextUnitSeq(lastSeq);
                } else if (ahead <= waitUpToMillis) {
                    listener.onClockRegression(name, backwards, "wait");
                    pause(ahead);
                    return RETRY;
                } else {
                    listener.onClockRegression(name, backwards, "fail");
                    throw new ClockBackwardsException(name, backwards);
                }
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
            pause(toMillis(toUnits(binding.floor()) + 1) - nowMillis);
            return RETRY;
        }
        long ceiling = binding.ceiling();
        if (useMillis + unitMillis - 1 > ceiling) {
            return windowExceeded(useMillis, ceiling, waitStarted);
        }
        long next = use << seqBits | seq;
        return current.state.compareAndSet(state, next) ? next : RETRY;
    }

    private long windowExceeded(long useMillis, long ceiling, long waitStarted) {
        if (waitStarted == 0) {
            listener.onWindowExceeded(name);
        }
        if (waitStarted == 0 ? waitUpToNanos == 0 : System.nanoTime() - waitStarted >= waitUpToNanos) {
            throw new SlotWindowExceededException(name, useMillis, ceiling);
        }
        pause(MAX_PARK_MILLIS);
        return RETRY_BOUNDED;
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
