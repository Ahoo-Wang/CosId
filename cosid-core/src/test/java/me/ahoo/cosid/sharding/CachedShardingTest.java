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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.google.common.collect.Range;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

class CachedShardingTest {

    @Test
    void preciseShardingShouldDelegateWithoutUsingRangeCache() {
        RecordingSharding actual = new RecordingSharding();
        CachedSharding<Integer> cache = new CachedSharding<>(actual);

        assertEquals("node-7", cache.sharding(7));
        assertEquals("node-7", cache.sharding(7));

        assertEquals(2, actual.preciseCalls);
        assertEquals(0, actual.rangeCalls);
    }

    @Test
    void rangeShardingShouldCacheByEquivalentRangeKey() {
        RecordingSharding actual = new RecordingSharding();
        CachedSharding<Integer> cache = new CachedSharding<>(actual);

        Collection<String> first = cache.sharding(Range.closed(1, 3));
        Collection<String> second = cache.sharding(Range.closed(1, 3));

        assertSame(first, second);
        assertEquals(List.of("range-[1..3]"), first);
        assertEquals(1, actual.rangeCalls);
    }

    @Test
    void getEffectiveNodesShouldDelegateToActualSharding() {
        RecordingSharding actual = new RecordingSharding();
        CachedSharding<Integer> cache = new CachedSharding<>(actual);

        assertSame(actual.effectiveNodes, cache.getEffectiveNodes());
    }

    private static final class RecordingSharding implements Sharding<Integer> {
        private final Collection<String> effectiveNodes = new ExactCollection<>("node-1", "node-2");
        private int preciseCalls;
        private int rangeCalls;

        @Override
        public String sharding(Integer shardingValue) {
            preciseCalls++;
            return "node-" + shardingValue;
        }

        @Override
        public Collection<String> sharding(Range<Integer> shardingValue) {
            rangeCalls++;
            return List.of("range-" + shardingValue);
        }

        @Override
        public Collection<String> getEffectiveNodes() {
            return effectiveNodes;
        }
    }
}
