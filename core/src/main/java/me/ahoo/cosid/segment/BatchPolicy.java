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
 * {@code min(maxBatch, roundUpToStep(rate * lookahead))}, at least {@code step}. Immutable; built with
 * {@link #builder()}, the only way to create one, so fields can be added with a default (api-freeze.md §4).
 */
public final class BatchPolicy {
    public static final long DEFAULT_STEP = 100;
    /** The default first value of a new key: 1, never 0. */
    public static final long DEFAULT_INITIAL_OFFSET = 1;
    /** The default {@code maxBatch} is at least this many values, whatever the step. */
    public static final long DEFAULT_MAX_BATCH_VALUES = 1L << 20;
    /** Every default: step 100, initial offset 1, lookahead 1s, the default max batch, reserve timeout 3s, no range ttl. */
    public static final BatchPolicy DEFAULT = builder().build();

    private final long step;
    private final long initialOffset;
    private final Duration lookahead;
    private final long maxBatch;
    private final Duration reserveTimeout;
    private final Optional<Duration> rangeTtl;

    private BatchPolicy(Builder builder) {
        this.step = builder.step;
        this.initialOffset = builder.initialOffset;
        this.lookahead = builder.lookahead;
        if (step <= 0) {
            throw new IllegalArgumentException("step must be positive: " + step);
        }
        this.maxBatch = builder.maxBatch == null ? defaultMaxBatch(step) : builder.maxBatch;
        this.reserveTimeout = builder.reserveTimeout;
        this.rangeTtl = Optional.ofNullable(builder.rangeTtl);
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

    /**
     * A builder starting from the defaults ({@link #DEFAULT}).
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The default {@code maxBatch} of a step: {@code max(step * 1000, 2^20)} rounded up to the step. The batch follows
     * the rate within the lookahead budget, so the cap only bounds what one reservation may burn; it does not depend
     * on a small step, so a fast generator with a small step is not held to many small serial reservations.
     *
     * @param step the step, positive
     * @return the default largest reservation, a multiple of {@code step}
     */
    public static long defaultMaxBatch(long step) {
        if (step <= 0) {
            throw new IllegalArgumentException("step must be positive: " + step);
        }
        if (step >= Long.MAX_VALUE / 1000) {
            return step;
        }
        long values = Math.max(step * 1000, DEFAULT_MAX_BATCH_VALUES);
        return Math.ceilDiv(values, step) * step;
    }

    /**
     * The smallest reservation.
     *
     * @return the step, positive; default {@value #DEFAULT_STEP}
     */
    public long step() {
        return step;
    }

    /**
     * Where the first range of a new key starts; 4.x started at 1, and {@code IdFiller} and most databases take 0 as
     * unset, so a generator should never issue 0.
     *
     * @return the initial offset; default {@value #DEFAULT_INITIAL_OFFSET}
     */
    public long initialOffset() {
        return initialOffset;
    }

    /**
     * How long the ids at hand should last; {@link Duration#ZERO} disables prefetching.
     *
     * @return the lookahead; default 1s
     */
    public Duration lookahead() {
        return lookahead;
    }

    /**
     * The largest reservation, a multiple of {@code step}.
     *
     * @return the largest reservation; default {@link #defaultMaxBatch(long)} of the step
     */
    public long maxBatch() {
        return maxBatch;
    }

    /**
     * How long a caller waits for a refill.
     *
     * @return the timeout; default 3s
     */
    public Duration reserveTimeout() {
        return reserveTimeout;
    }

    /**
     * A range unused for this long is dropped (4.x segment ttl).
     *
     * @return the range ttl; default none
     */
    public Optional<Duration> rangeTtl() {
        return rangeTtl;
    }

    /**
     * This policy with another step. A {@code maxBatch} equal to the default of the current step becomes the default
     * of the new one; any other is kept and must be a multiple of the new step.
     *
     * @param step the step, positive
     * @return the policy
     */
    public BatchPolicy withStep(long step) {
        Builder builder = toBuilder();
        if (maxBatch == defaultMaxBatch(this.step)) {
            builder.maxBatch = null;
        }
        return builder.step(step).build();
    }

    /**
     * This policy with another lookahead.
     *
     * @param lookahead the lookahead, not negative
     * @return the policy
     */
    public BatchPolicy withLookahead(Duration lookahead) {
        return toBuilder().lookahead(lookahead).build();
    }

    private Builder toBuilder() {
        Builder builder = new Builder().step(step).initialOffset(initialOffset).lookahead(lookahead).maxBatch(maxBatch)
            .reserveTimeout(reserveTimeout);
        rangeTtl.ifPresent(builder::rangeTtl);
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BatchPolicy that && step == that.step && initialOffset == that.initialOffset && lookahead.equals(that.lookahead)
            && maxBatch == that.maxBatch && reserveTimeout.equals(that.reserveTimeout) && rangeTtl.equals(that.rangeTtl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(step, initialOffset, lookahead, maxBatch, reserveTimeout, rangeTtl);
    }

    @Override
    public String toString() {
        return "BatchPolicy[step=" + step + ", initialOffset=" + initialOffset + ", lookahead=" + lookahead + ", maxBatch=" + maxBatch
            + ", reserveTimeout=" + reserveTimeout + ", rangeTtl=" + rangeTtl + "]";
    }

    /**
     * Builds a {@link BatchPolicy}; {@link #build()} checks the fields.
     */
    public static final class Builder {
        private long step = DEFAULT_STEP;
        private long initialOffset = DEFAULT_INITIAL_OFFSET;
        private Duration lookahead = Duration.ofSeconds(1);
        private Long maxBatch;
        private Duration reserveTimeout = Duration.ofSeconds(3);
        private Duration rangeTtl;

        private Builder() {
        }

        /**
         * Sets the smallest reservation.
         *
         * @param step the step, positive; default {@value BatchPolicy#DEFAULT_STEP}
         * @return this builder
         */
        public Builder step(long step) {
            this.step = step;
            return this;
        }

        /**
         * Sets where the first range of a new key starts.
         *
         * @param initialOffset the offset, not negative; default {@value BatchPolicy#DEFAULT_INITIAL_OFFSET}, so a
         *                      generator never issues 0
         * @return this builder
         */
        public Builder initialOffset(long initialOffset) {
            this.initialOffset = initialOffset;
            return this;
        }

        /**
         * Sets how long the ids at hand should last.
         *
         * @param lookahead the lookahead, not negative; default 1s; {@link Duration#ZERO} disables prefetching
         * @return this builder
         */
        public Builder lookahead(Duration lookahead) {
            this.lookahead = Objects.requireNonNull(lookahead, "lookahead");
            return this;
        }

        /**
         * Sets the largest reservation.
         *
         * @param maxBatch a positive multiple of the step; default {@link BatchPolicy#defaultMaxBatch(long)} of the
         *                 step
         * @return this builder
         */
        public Builder maxBatch(long maxBatch) {
            this.maxBatch = maxBatch;
            return this;
        }

        /**
         * Sets how long a caller waits for a refill.
         *
         * @param reserveTimeout the timeout, positive; default 3s
         * @return this builder
         */
        public Builder reserveTimeout(Duration reserveTimeout) {
            this.reserveTimeout = Objects.requireNonNull(reserveTimeout, "reserveTimeout");
            return this;
        }

        /**
         * Sets how long a range may stay unused before it is dropped (4.x segment ttl).
         *
         * @param rangeTtl the range ttl, positive; default none
         * @return this builder
         */
        public Builder rangeTtl(Duration rangeTtl) {
            this.rangeTtl = Objects.requireNonNull(rangeTtl, "rangeTtl");
            return this;
        }

        /**
         * Builds the policy.
         *
         * @return the policy
         * @throws IllegalArgumentException if a field is out of range
         */
        public BatchPolicy build() {
            return new BatchPolicy(this);
        }
    }
}
