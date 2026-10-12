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

import me.ahoo.cosid.snowflake.exception.ClockBackwardsException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongSupplier;

/**
 * The timestamp and sequence logic shared by the snowflake and CosId generators.
 *
 * <p><b>Internal:</b> not part of the CosId API, it may change in any release.</p>
 *
 * <p>Within one time unit the sequence increments; when it wraps around, {@link #next()} waits for the next time unit.
 * When a new time unit starts and the sequence has passed {@code sequenceResetThreshold}, it restarts from 1, which
 * keeps low-traffic ids from all ending in the same sequence values. A clock that moved backwards throws
 * {@link ClockBackwardsException}. Not thread-safe: callers synchronize.</p>
 *
 * @author ahoo wang
 */
public final class TimestampSequencer {
    private final LongSupplier timeSource;
    private final long maxSequence;
    private final long sequenceResetThreshold;
    private final Runnable waitForNextTick;
    private long sequence = 0L;
    private long lastTimestamp = -1L;
    
    /**
     * Create a sequencer.
     *
     * @param timeSource             current time in the generator's unit (milliseconds or seconds)
     * @param maxSequence            largest sequence, a power of two minus one
     * @param sequenceResetThreshold sequences at or above this restart in the next time unit
     */
    public TimestampSequencer(LongSupplier timeSource, long maxSequence, long sequenceResetThreshold) {
        this(timeSource, maxSequence, sequenceResetThreshold, Thread::onSpinWait);
    }
    
    /**
     * Create a sequencer.
     *
     * @param waitForNextTick called while the sequence of the current tick is used up, until the time source moves on:
     *                        {@link Thread#onSpinWait()} for milliseconds, {@link #parkUntilNextSecond()} for seconds
     */
    public TimestampSequencer(LongSupplier timeSource, long maxSequence, long sequenceResetThreshold, Runnable waitForNextTick) {
        this.timeSource = timeSource;
        this.maxSequence = maxSequence;
        this.sequenceResetThreshold = sequenceResetThreshold;
        this.waitForNextTick = waitForNextTick;
    }
    
    /**
     * Parks until the next second of the system clock, at most 10 ms at a time, instead of spinning for up to a second.
     */
    public static void parkUntilNextSecond() {
        long untilNextSecond = 1000 - System.currentTimeMillis() % 1000;
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(Math.min(untilNextSecond, 10)));
    }
    
    /**
     * Advance to the next (timestamp, sequence) pair; read it with {@link #getLastTimestamp()} and {@link #getSequence()}.
     *
     * @return the timestamp of the new pair
     * @throws ClockBackwardsException if the clock moved back behind the last timestamp
     */
    public long next() {
        long currentTimestamp = timeSource.getAsLong();
        if (currentTimestamp < lastTimestamp) {
            throw new ClockBackwardsException(lastTimestamp, currentTimestamp);
        }
        if (currentTimestamp > lastTimestamp && sequence >= sequenceResetThreshold) {
            sequence = 0L;
        }
        sequence = (sequence + 1) & maxSequence;
        if (sequence == 0L) {
            currentTimestamp = nextTime();
        }
        lastTimestamp = currentTimestamp;
        return currentTimestamp;
    }
    
    /**
     * Waits for the next tick after the sequence of the last one was used up. If the clock moves back while waiting, it
     * throws instead of waiting for the clock to catch up, and the last tick stays used up.
     */
    private long nextTime() {
        long time = timeSource.getAsLong();
        while (time <= lastTimestamp) {
            if (time < lastTimestamp) {
                // The sequence of the last tick is used up: generating again in it would repeat an id.
                sequence = maxSequence;
                throw new ClockBackwardsException(lastTimestamp, time);
            }
            waitForNextTick.run();
            time = timeSource.getAsLong();
        }
        return time;
    }
    
    public long getSequence() {
        return sequence;
    }
    
    public long getLastTimestamp() {
        return lastTimestamp;
    }
}
