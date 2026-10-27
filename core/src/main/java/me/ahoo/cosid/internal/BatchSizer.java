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

import java.time.Duration;

/**
 * Sizes reservations by a time budget (04-segment.md §2): {@code min(maxBatch, roundUpToStep(rate * lookahead))}, at
 * least {@code step}. The rate is estimated from how fast whole ranges are used up, smoothed so that one burst does
 * not size the next reservation alone. Not thread-safe: the generator calls it under its switch lock.
 */
public final class BatchSizer {
    private final long step;
    private final long maxBatch;
    private final double lookaheadMillis;
    /** Values per millisecond; 0 until a range was used up. */
    private double rate;
    private long batch;

    /**
     * A sizer that starts at {@code step}.
     *
     * @param step      the smallest reservation
     * @param maxBatch  the largest reservation, a multiple of {@code step}
     * @param lookahead how long the values at hand should last
     */
    public BatchSizer(long step, long maxBatch, Duration lookahead) {
        this.step = step;
        this.maxBatch = maxBatch;
        this.lookaheadMillis = lookahead.toNanos() / 1_000_000.0;
        this.batch = step;
    }

    /**
     * The size of the next reservation.
     *
     * @return between {@code step} and {@code maxBatch}, a multiple of {@code step}
     */
    public long batch() {
        return batch;
    }

    public double rate() {
        return rate;
    }

    /**
     * The values the lookahead needs at the estimated rate.
     *
     * @return {@code rate * lookahead}, 0 before the first estimate
     */
    public long budget() {
        double budget = rate * lookaheadMillis;
        return budget >= Long.MAX_VALUE / 2.0 ? Long.MAX_VALUE / 2 : (long) budget;
    }

    /**
     * Records that {@code values} were used up in {@code elapsedMillis}.
     *
     * @param values        the size of the range used up
     * @param elapsedMillis how long it lasted; less than a millisecond counts as one
     * @return the previous batch size if the batch changed, otherwise {@code -1}
     */
    public long record(long values, long elapsedMillis) {
        double sample = (double) values / Math.max(1L, elapsedMillis);
        rate = rate == 0 ? sample : (rate + sample) / 2;
        long previous = batch;
        batch = sizeOf(rate * lookaheadMillis);
        return batch == previous ? -1L : previous;
    }

    private long sizeOf(double budget) {
        if (!(budget > step)) {
            return step;
        }
        if (budget >= maxBatch) {
            return maxBatch;
        }
        long steps = (long) Math.ceil(budget / step);
        return Math.min(maxBatch, steps * step);
    }
}
