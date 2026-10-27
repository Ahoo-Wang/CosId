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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.time.TimeSource;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;

class SegmentGeneratorsTest {
    private final RangeStore store = (key, size, initialOffset) -> new Range(initialOffset, initialOffset + size);

    @Test
    void rangeStoreDefaults() {
        assertThat(store.seal("app/order"), is(false));
        store.close();
    }

    @Test
    void segmentGeneratorKeepsItsConfiguration() {
        SegmentGenerator generator = new SegmentGenerator("order", "app/order", store, BatchPolicy.DEFAULT, Runnable::run,
            TimeSource.SYSTEM, GeneratorListener.NOOP);
        assertThat(generator.name(), is("order"));
        assertThat(generator.key(), is("app/order"));
        assertThat(generator.policy(), sameInstance(BatchPolicy.DEFAULT));
        assertThrows(NullPointerException.class, () -> new SegmentGenerator("order", null, store, BatchPolicy.DEFAULT,
            Runnable::run, TimeSource.SYSTEM, GeneratorListener.NOOP));
    }

    @Test
    void periodicGeneratorKeepsItsConfiguration() {
        PeriodicSegmentGenerator generator = new PeriodicSegmentGenerator("bill", "app/bill", Period.YEAR_MONTH_DAY, ZoneOffset.UTC,
            store, BatchPolicy.DEFAULT, PeriodicPolicy.DEFAULT, Runnable::run, TimeSource.SYSTEM, GeneratorListener.NOOP);
        assertThat(generator.name(), is("bill"));
        assertThat(Period.YEAR, is((Object) me.ahoo.cosid.internal.PatternPeriod.YEAR));
        assertThat(me.ahoo.cosid.internal.PatternPeriod.YEAR_MONTH.pattern(), is("yyyyMM"));
    }

    @Test
    void periodicPolicyRejectsNegativeDurations() {
        assertThrows(IllegalArgumentException.class, () -> new PeriodicPolicy(Duration.ofSeconds(-1), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new PeriodicPolicy(Duration.ZERO, Optional.of(Duration.ofSeconds(-1))));
        assertThat(new PeriodicPolicy(Duration.ZERO, Optional.of(Duration.ofDays(1))).sealAfter(), is(Optional.of(Duration.ofDays(1))));
    }
}
