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
 * How a {@link PeriodicSegmentGenerator} moves between periods (04-segment.md §3).
 *
 * @param prewarm   the next period is created and warmed up this long before the current one ends; default 1min
 * @param sealAfter the key of a period is sealed this long after the period ends (at least a day is advised);
 *                  default never
 */
public record PeriodicPolicy(Duration prewarm, Optional<Duration> sealAfter) {
    public static final PeriodicPolicy DEFAULT = new PeriodicPolicy(Duration.ofMinutes(1), Optional.empty());

    public PeriodicPolicy {
        Objects.requireNonNull(prewarm, "prewarm");
        Objects.requireNonNull(sealAfter, "sealAfter");
        if (prewarm.isNegative()) {
            throw new IllegalArgumentException("prewarm must not be negative: " + prewarm);
        }
        if (sealAfter.isPresent() && sealAfter.get().isNegative()) {
            throw new IllegalArgumentException("sealAfter must not be negative: " + sealAfter.get());
        }
    }
}
