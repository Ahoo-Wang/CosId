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

package me.ahoo.cosid.time;

import java.time.Duration;
import java.util.Objects;

/**
 * What a time-based generator does when the clock goes back (03-core-model.md §4): up to {@code borrowUpTo} it keeps
 * issuing in the last time unit (borrowing from the future), up to {@code waitUpTo} it waits, beyond that it throws
 * {@code ClockBackwardsException}. {@code waitUpTo} also bounds the wait for a renewal at the slot window ceiling.
 *
 * @param borrowUpTo the largest regression issued on the last time unit
 * @param waitUpTo   the largest regression waited out
 */
public record ClockRegressionPolicy(Duration borrowUpTo, Duration waitUpTo) {
    public static final ClockRegressionPolicy DEFAULT = new ClockRegressionPolicy(Duration.ofMillis(5), Duration.ofSeconds(1));
    public static final ClockRegressionPolicy FAIL = new ClockRegressionPolicy(Duration.ZERO, Duration.ZERO);

    public ClockRegressionPolicy {
        Objects.requireNonNull(borrowUpTo, "borrowUpTo");
        Objects.requireNonNull(waitUpTo, "waitUpTo");
        if (borrowUpTo.isNegative() || waitUpTo.isNegative()) {
            throw new IllegalArgumentException("borrowUpTo and waitUpTo must not be negative");
        }
    }
}
