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

package me.ahoo.cosid.runtime;

import me.ahoo.cosid.segment.BatchPolicy;
import me.ahoo.cosid.segment.Period;
import me.ahoo.cosid.segment.PeriodicPolicy;
import me.ahoo.cosid.time.ClockRegressionPolicy;
import me.ahoo.cosid.time.Layout;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * A generator of a {@link RuntimeSpec}, by strategy.
 */
public sealed interface GeneratorSpec {
    /**
     * A time-based generator packing {@code long} ids.
     *
     * @param layout     the layout; its node bits must cover the machine bits
     * @param regression the clock regression policy
     * @param codec      the codec list, applied in order; empty for none
     */
    record Snowflake(Layout layout, ClockRegressionPolicy regression, List<CodecSpec> codec) implements GeneratorSpec {
        public Snowflake {
            Objects.requireNonNull(layout, "layout");
            Objects.requireNonNull(regression, "regression");
            codec = List.copyOf(Objects.requireNonNull(codec, "codec"));
        }
    }

    /**
     * The 80-bit CosId ({@link Layout#COSID_80}), text only.
     *
     * @param regression the clock regression policy
     * @param codec      the parts codec
     */
    record CosId80(ClockRegressionPolicy regression, PartsCodecSpec codec) implements GeneratorSpec {
        public CosId80 {
            Objects.requireNonNull(regression, "regression");
            Objects.requireNonNull(codec, "codec");
        }
    }

    /**
     * A segment generator.
     *
     * @param store the store name
     * @param batch the batch policy
     * @param codec the codec list, applied in order; empty for none
     */
    record Segment(String store, BatchPolicy batch, List<CodecSpec> codec) implements GeneratorSpec {
        public Segment {
            Objects.requireNonNull(store, "store");
            Objects.requireNonNull(batch, "batch");
            codec = List.copyOf(Objects.requireNonNull(codec, "codec"));
        }
    }

    /**
     * A periodic segment generator.
     *
     * @param store    the store name
     * @param period   the period
     * @param zone     the time zone the periods follow
     * @param batch    the batch policy of each period
     * @param periodic the periodic policy
     * @param codec    the grouped codec
     */
    record PeriodicSegment(String store, Period period, ZoneId zone, BatchPolicy batch, PeriodicPolicy periodic,
                           GroupedCodecSpec codec) implements GeneratorSpec {
        public PeriodicSegment {
            Objects.requireNonNull(store, "store");
            Objects.requireNonNull(period, "period");
            Objects.requireNonNull(zone, "zone");
            Objects.requireNonNull(batch, "batch");
            Objects.requireNonNull(periodic, "periodic");
            Objects.requireNonNull(codec, "codec");
        }
    }
}
