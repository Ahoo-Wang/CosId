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

package me.ahoo.cosid.sharding;

import com.google.common.collect.Range;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;

import static me.ahoo.cosid.sharding.IntervalTimelineTest.*;

/**
 * @author ahoo wang
 */
class CachedShardingTest {
    private Sharding<LocalDateTime> actual;
    private CachedSharding<LocalDateTime> cache;

    @BeforeEach
    void init() {
        IntervalStep step = IntervalStep.of(ChronoUnit.MONTHS);
        actual = new IntervalTimeline(LOGIC_NAME, Range.closed(LOWER_DATE_TIME, UPPER_DATE_TIME), step, SUFFIX_FORMATTER);
        cache = new CachedSharding<>(actual);
    }


    @ParameterizedTest
    @MethodSource("me.ahoo.cosid.sharding.IntervalTimelineTest#shardingArgsProvider")
    void sharding(LocalDateTime dateTime, String expected) {
        String actual = cache.sharding(dateTime);
        Assertions.assertEquals(expected, actual);
    }


    @ParameterizedTest
    @MethodSource("me.ahoo.cosid.sharding.IntervalTimelineTest#shardingRangeArgsProvider")
    void shardingRange(Range<LocalDateTime> shardingValue, Collection<String> expected) {
        Collection<String> actual = cache.sharding(shardingValue);
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void shardingRangeCache() {
        Collection<String> actual = cache.sharding(Range.singleton(LOWER_DATE_TIME));
        Collection<String> actual2 = cache.sharding(Range.singleton(LOWER_DATE_TIME));
        Assertions.assertEquals(actual, actual2);
    }

    @Test
    void getEffectiveNodes() {
        Assertions.assertEquals(ALL_NODES, cache.getEffectiveNodes());
    }
}
