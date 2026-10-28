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
 * How a {@link PeriodicSegmentGenerator} moves between periods (04-segment.md §3). Immutable; built with
 * {@link #builder()}, the only way to create one, so fields can be added with a default (api-freeze.md §4).
 */
public final class PeriodicPolicy {
    /** Every default: prewarm 1min, never sealed. */
    public static final PeriodicPolicy DEFAULT = builder().build();

    private final Duration prewarm;
    private final Optional<Duration> sealAfter;

    private PeriodicPolicy(Builder builder) {
        this.prewarm = builder.prewarm;
        this.sealAfter = Optional.ofNullable(builder.sealAfter);
        if (prewarm.isNegative()) {
            throw new IllegalArgumentException("prewarm must not be negative: " + prewarm);
        }
        if (sealAfter.isPresent() && sealAfter.get().isNegative()) {
            throw new IllegalArgumentException("sealAfter must not be negative: " + sealAfter.get());
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
     * The next period is created and warmed up this long before the current one ends.
     *
     * @return the prewarm; default 1min
     */
    public Duration prewarm() {
        return prewarm;
    }

    /**
     * The key of a period is sealed this long after the period ends (at least a day is advised).
     *
     * @return the delay; default none, never sealed
     */
    public Optional<Duration> sealAfter() {
        return sealAfter;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PeriodicPolicy that && prewarm.equals(that.prewarm) && sealAfter.equals(that.sealAfter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prewarm, sealAfter);
    }

    @Override
    public String toString() {
        return "PeriodicPolicy[prewarm=" + prewarm + ", sealAfter=" + sealAfter + "]";
    }

    /**
     * Builds a {@link PeriodicPolicy}; {@link #build()} checks the fields.
     */
    public static final class Builder {
        private Duration prewarm = Duration.ofMinutes(1);
        private Duration sealAfter;

        private Builder() {
        }

        /**
         * Sets how long before the current period ends the next one is created and warmed up.
         *
         * @param prewarm the prewarm, not negative; default 1min
         * @return this builder
         */
        public Builder prewarm(Duration prewarm) {
            this.prewarm = Objects.requireNonNull(prewarm, "prewarm");
            return this;
        }

        /**
         * Sets how long after a period ends its key is sealed (at least a day is advised).
         *
         * @param sealAfter the delay, not negative; default none, never sealed
         * @return this builder
         */
        public Builder sealAfter(Duration sealAfter) {
            this.sealAfter = Objects.requireNonNull(sealAfter, "sealAfter");
            return this;
        }

        /**
         * Builds the policy.
         *
         * @return the policy
         * @throws IllegalArgumentException if a field is negative
         */
        public PeriodicPolicy build() {
            return new PeriodicPolicy(this);
        }
    }
}
