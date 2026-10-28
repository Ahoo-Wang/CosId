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
import me.ahoo.cosid.internal.Ticker;
import me.ahoo.cosid.internal.WindowFile;
import me.ahoo.cosid.time.TimeSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A slot the user keeps unique (manual or StatefulSet ordinal), with a time window from a local file
 * (05-machine-lease.md §4.4): the file holds the ceiling, written ahead and fsynced before use, so a crash followed by
 * a clock regression cannot repeat ids. Without a file there is no regression protection.
 *
 * <p>With a file, one long-lived task on the executor writes the ceiling ahead again whenever less than
 * {@code ahead/2} of the window is left; the executor must run tasks asynchronously.
 */
public final class StaticSlot implements SlotHandle, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(StaticSlot.class);
    private static final long FIRST_BACKOFF_NANOS = TimeUnit.MILLISECONDS.toNanos(100);

    private final int slot;
    private final int bits;
    private final Optional<Instant> notBefore;
    private final Executor executor;
    private final TimeSource time;
    private final Ticker ticker;
    private final Optional<WindowFile> window;
    private final long aheadMillis;
    private final long maxWaitNanos;

    /** Guards the state; never held across file I/O (design principle 7). */
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition wakeUp = lock.newCondition();
    /** Serializes the writes of the window file, which share one temporary file; never taken under {@link #lock}. */
    private final ReentrantLock fileLock = new ReentrantLock();
    private volatile boolean started;
    private volatile boolean closed;
    private volatile SlotBinding binding;
    private long retryAtNanos;
    private long backoffNanos;
    private volatile Thread starter;
    private volatile boolean ranInline;

    /**
     * A static slot; {@link #start()} reads the window.
     *
     * @param slot      the slot, in {@code [0, 2^bits)}
     * @param bits      the slot bits
     * @param stateFile the window file; none means no clock regression protection
     * @param ahead     how far ahead the ceiling is written; default 10s
     * @param notBefore the floor when the file does not exist yet (given by the migration tool)
     * @param executor  runs the asynchronous rewrites of the ceiling
     * @param time      the time source
     */
    public StaticSlot(int slot, int bits, Optional<Path> stateFile, Duration ahead, Optional<Instant> notBefore,
                      Executor executor, TimeSource time) {
        this(slot, bits, stateFile, ahead, notBefore, executor, time, Ticker.SYSTEM);
    }

    StaticSlot(int slot, int bits, Optional<Path> stateFile, Duration ahead, Optional<Instant> notBefore,
               Executor executor, TimeSource time, Ticker ticker) {
        if (bits < 1 || bits > Integer.SIZE - 2) {
            throw new IllegalArgumentException("bits must be in [1, " + (Integer.SIZE - 2) + "]: " + bits);
        }
        if (slot < 0 || slot >= 1 << bits) {
            throw new IllegalArgumentException("slot must be in [0, " + (1 << bits) + "): " + slot);
        }
        this.slot = slot;
        this.bits = bits;
        Objects.requireNonNull(stateFile, "stateFile");
        Objects.requireNonNull(ahead, "ahead");
        this.notBefore = Objects.requireNonNull(notBefore, "notBefore");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.time = Objects.requireNonNull(time, "time");
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        if (ahead.toMillis() < 2) {
            throw new IllegalArgumentException("ahead must be at least 2ms: " + ahead);
        }
        this.window = stateFile.map(WindowFile::new);
        this.aheadMillis = ahead.toMillis();
        this.maxWaitNanos = ahead.toNanos() / 4;
    }

    public int slot() {
        return slot;
    }

    public int bits() {
        return bits;
    }

    /**
     * Reads the floor from the file and writes the first ceiling ahead.
     */
    public void start() {
        lock.lock();
        try {
            if (started || closed) {
                throw new IllegalStateException("StaticSlot " + slot + " was already started");
            }
            started = true;
        } finally {
            lock.unlock();
        }
        long floor = notBefore.map(Instant::toEpochMilli).orElse(0L);
        if (window.isEmpty()) {
            publish(new SlotBinding(slot, floor, Long.MAX_VALUE, 1));
            log.warn("StaticSlot {} has no window file: a clock regression across a restart can repeat ids", slot);
            return;
        }
        WindowFile file = window.get();
        long ceiling;
        fileLock.lock();
        try {
            OptionalLong persisted = file.read();
            if (persisted.isPresent()) {
                floor = Math.max(floor, persisted.getAsLong());
            }
            // Write ahead: the ceiling is durable before any id can use it.
            ceiling = Math.max(floor, time.currentMillis() + aheadMillis);
            file.write(ceiling);
        } finally {
            fileLock.unlock();
        }
        publish(new SlotBinding(slot, floor, ceiling, 1));
        starter = Thread.currentThread();
        log.info("StaticSlot {} window ({}, {}] from {}", slot, floor, ceiling, file.path());
        try {
            executor.execute(this::extendLoop);
        } catch (RejectedExecutionException e) {
            close();
            throw new CosIdException("The executor rejected the window extensions of StaticSlot " + slot, e);
        } finally {
            starter = null;
        }
        if (ranInline) {
            close();
            throw new IllegalArgumentException("The executor of StaticSlot must run tasks asynchronously, not on the calling thread");
        }
    }

    /** Publishes the first binding unless {@link #close} came first; the file then keeps the ceiling written ahead. */
    private void publish(SlotBinding first) {
        lock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("StaticSlot " + slot + " was closed while starting");
            }
            binding = first;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public SlotBinding current() {
        SlotBinding current = binding;
        if (current == null) {
            throw new IllegalStateException("StaticSlot " + slot + " is not started");
        }
        return current;
    }

    /**
     * Writes the exact last issued timestamp as the ceiling, so the next start begins right after it.
     *
     * @param lastIssued the largest timestamp issued on the slot, in milliseconds
     */
    public void close(long lastIssued) {
        SlotBinding current;
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            wakeUp.signalAll();
            current = binding;
            if (current == null) {
                return;
            }
            current.invalidate();
        } finally {
            lock.unlock();
        }
        if (window.isEmpty()) {
            return;
        }
        // Waits for an extension being written, which then finds the slot closed and writes no more: this write is the last.
        fileLock.lock();
        try {
            long next = Math.max(current.floor(), Math.min(lastIssued, current.ceiling()));
            if (next < current.ceiling()) {
                window.get().write(next);
            }
        } catch (RuntimeException e) {
            // The ceiling written ahead stays: the next start begins later, never earlier.
            log.warn("StaticSlot {} could not write its last issued timestamp", slot, e);
        } finally {
            fileLock.unlock();
        }
    }

    /**
     * Stops extending the window; the ceiling written ahead stays in the file.
     */
    @Override
    public void close() {
        close(Long.MAX_VALUE);
    }

    private void extendLoop() {
        if (Thread.currentThread() == starter) {
            ranInline = true;
            return;
        }
        try {
            while (true) {
                long waitNanos = extendIfDue();
                if (waitNanos < 0) {
                    return;
                }
                lock.lock();
                try {
                    if (closed) {
                        return;
                    }
                    wakeUp.awaitNanos(waitNanos);
                } finally {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            if (!closed) {
                log.error("The window extensions of StaticSlot {} were interrupted; generators stop at the ceiling", slot);
            }
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Writes the ceiling ahead again if less than {@code ahead/2} of the window is left; what the extension task does
     * on each wake-up. The write and its fsync run without the state lock; the ceiling is raised only after it.
     *
     * @return nanoseconds until the next check, or -1 when the slot does not run with a file
     */
    long extendIfDue() {
        SlotBinding current;
        long next;
        lock.lock();
        try {
            if (!started || closed || window.isEmpty() || binding == null) {
                return -1;
            }
            long nowNanos = ticker.nanoTime();
            if (nowNanos < retryAtNanos) {
                return retryAtNanos - nowNanos;
            }
            current = binding;
            long now = time.currentMillis();
            if (current.ceiling() - now >= aheadMillis / 2) {
                return untilDue(current);
            }
            next = Math.max(current.ceiling(), now + aheadMillis);
        } finally {
            lock.unlock();
        }
        fileLock.lock();
        try {
            if (closed) {
                // close() owns the last write.
                return -1;
            }
            window.get().write(next);
        } catch (RuntimeException e) {
            long backoff;
            lock.lock();
            try {
                backoffNanos = Math.min(backoffNanos == 0 ? FIRST_BACKOFF_NANOS : backoffNanos * 2, Math.max(1, maxWaitNanos));
                backoff = backoffNanos;
                retryAtNanos = ticker.nanoTime() + backoff;
            } finally {
                lock.unlock();
            }
            log.warn("StaticSlot {} could not extend its window; retrying in {}ms", slot, TimeUnit.NANOSECONDS.toMillis(backoff), e);
            return backoff;
        } finally {
            fileLock.unlock();
        }
        lock.lock();
        try {
            backoffNanos = 0;
            // Durable first, then raised (write ahead); raising a binding close() invalidated meanwhile issues nothing.
            current.raiseCeiling(next);
        } finally {
            lock.unlock();
        }
        return untilDue(current);
    }

    private long untilDue(SlotBinding current) {
        long untilDue = TimeUnit.MILLISECONDS.toNanos(current.ceiling() - time.currentMillis() - aheadMillis / 2);
        return Math.max(1, Math.min(untilDue, maxWaitNanos));
    }
}
