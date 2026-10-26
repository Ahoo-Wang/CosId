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

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * How a {@link SegmentGenerator} reserves ranges (04-segment.md §2). A reservation is
 * {@code min(maxBatch, roundUpToStep(rate * lookahead))}, at least {@code step}.
 *
 * @param step           the smallest reservation, positive; default 100
 * @param initialOffset  where the first range of a new key starts; default 0
 * @param lookahead      how long the ids at hand should last; default 1s; {@link Duration#ZERO} disables prefetching
 * @param maxBatch       the largest reservation, a multiple of {@code step}; default {@code step * 1000}
 * @param reserveTimeout how long a caller waits for a refill; default 3s
 * @param rangeTtl       a range unused for this long is dropped (4.x segment ttl); default none
 */
public record BatchPolicy(long step,
                          long initialOffset,
                          Duration lookahead,
                          long maxBatch,
                          Duration reserveTimeout,
                          Optional<Duration> rangeTtl) {
    public static final long DEFAULT_STEP = 100;
    public static final BatchPolicy DEFAULT = new BatchPolicy(DEFAULT_STEP, 0, Duration.ofSeconds(1), DEFAULT_STEP * 1000,
        Duration.ofSeconds(3), Optional.empty());

    public BatchPolicy {
        Objects.requireNonNull(lookahead, "lookahead");
        Objects.requireNonNull(reserveTimeout, "reserveTimeout");
        Objects.requireNonNull(rangeTtl, "rangeTtl");
        if (step <= 0) {
            throw new IllegalArgumentException("step must be positive: " + step);
        }
        if (initialOffset < 0) {
            throw new IllegalArgumentException("initialOffset must not be negative: " + initialOffset);
        }
        if (lookahead.isNegative()) {
            throw new IllegalArgumentException("lookahead must not be negative: " + lookahead);
        }
        if (maxBatch < step || maxBatch % step != 0) {
            throw new IllegalArgumentException("maxBatch must be a positive multiple of step " + step + ": " + maxBatch);
        }
        if (reserveTimeout.isNegative() || reserveTimeout.isZero()) {
            throw new IllegalArgumentException("reserveTimeout must be positive: " + reserveTimeout);
        }
        if (rangeTtl.isPresent() && (rangeTtl.get().isNegative() || rangeTtl.get().isZero())) {
            throw new IllegalArgumentException("rangeTtl must be positive: " + rangeTtl.get());
        }
    }
}
