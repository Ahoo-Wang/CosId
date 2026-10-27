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
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

class SegmentModelTest {
    @Test
    void rangeIsHalfOpenAndNonEmpty() {
        assertThat(new Range(0, 1).end(), is(1L));
        assertThrows(IllegalArgumentException.class, () -> new Range(1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Range(-1, 1));
    }

    @Test
    void batchPolicyDefaults() {
        assertThat(BatchPolicy.DEFAULT.step(), is(100L));
        assertThat(BatchPolicy.DEFAULT.initialOffset(), is(1L));
        assertThat(BatchPolicy.DEFAULT.maxBatch(), is(1_048_600L));
        assertThat(BatchPolicy.DEFAULT.lookahead(), is(Duration.ofSeconds(1)));
        assertThat(BatchPolicy.DEFAULT.reserveTimeout(), is(Duration.ofSeconds(3)));
        assertThat(BatchPolicy.DEFAULT.rangeTtl(), is(Optional.empty()));
        assertThat(PeriodicPolicy.DEFAULT.prewarm(), is(Duration.ofMinutes(1)));
    }

    @Test
    void defaultMaxBatchIsLargeAndAMultipleOfTheStep() {
        assertThat(BatchPolicy.defaultMaxBatch(1), is(1L << 20));
        assertThat(BatchPolicy.defaultMaxBatch(10), is(1_048_580L));
        assertThat(BatchPolicy.defaultMaxBatch(100), is(1_048_600L));
        assertThat(BatchPolicy.defaultMaxBatch(2000), is(2_000_000L));
        assertThat(BatchPolicy.defaultMaxBatch(Long.MAX_VALUE / 100), is(Long.MAX_VALUE / 100));
        assertThrows(IllegalArgumentException.class, () -> BatchPolicy.defaultMaxBatch(0));
    }

    @Test
    void batchPolicyRejectsInvalidValues() {
        Duration second = Duration.ofSeconds(1);
        assertThrows(IllegalArgumentException.class, () -> new BatchPolicy(0, 0, second, 100, second, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new BatchPolicy(100, -1, second, 100, second, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new BatchPolicy(100, 0, second, 150, second, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new BatchPolicy(100, 0, second, 100, Duration.ZERO, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new BatchPolicy(100, 0, second, 100, second, Optional.of(Duration.ZERO)));
        assertThrows(IllegalArgumentException.class, () -> new BatchPolicy(100, 0, second.negated(), 100, second, Optional.empty()));
    }
}
