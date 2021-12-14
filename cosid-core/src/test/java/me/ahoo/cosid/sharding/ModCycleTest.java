/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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
import org.junit.jupiter.api.Test;

import java.util.Collection;

/**
 * @author ahoo wang
 */
class ModCycleTest {

    public static ModCycle<Long> createModCycle() {
        return new ModCycle<>(4, "t_mod_");
    }


    @Test
    public void getDivisor() {
        ModCycle<Long> modCycle = createModCycle();
        Assertions.assertNotNull(modCycle);
        Assertions.assertEquals(4, modCycle.getDivisor());
    }


    @Test
    public void getEffectiveNodes() {
        ModCycle<Long> modCycle = createModCycle();
        Assertions.assertEquals(new ExactCollection<>("t_mod_0", "t_mod_1", "t_mod_2", "t_mod_3"), modCycle.getEffectiveNodes());
    }

    @Test
    public void shardingPrecise() {
        ModCycle<Long> modCycle = createModCycle();
        String node = modCycle.sharding(1L);
        Assertions.assertEquals("t_mod_1", node);
        node = modCycle.sharding(2L);
        Assertions.assertEquals("t_mod_2", node);
        node = modCycle.sharding(3L);
        Assertions.assertEquals("t_mod_3", node);
        node = modCycle.sharding(4L);
        Assertions.assertEquals("t_mod_0", node);
        node = modCycle.sharding(5L);
        Assertions.assertEquals("t_mod_1", node);
    }

    @Test
    public void shardingRange() {
        ModCycle<Long> modCycle = createModCycle();
        Collection<String> nodes = modCycle.sharding(Range.closed(1L, 3L));
        Assertions.assertEquals(new ExactCollection<>("t_mod_1", "t_mod_2", "t_mod_3"), nodes);
        nodes = modCycle.sharding(Range.closedOpen(1L, 3L));
        Assertions.assertEquals(new ExactCollection<>("t_mod_1", "t_mod_2"), nodes);
        nodes = modCycle.sharding(Range.openClosed(1L, 3L));

        Assertions.assertEquals(new ExactCollection<>("t_mod_2", "t_mod_3"), nodes);
        nodes = modCycle.sharding(Range.open(1L, 3L));
        Assertions.assertEquals(new ExactCollection<>("t_mod_2"), nodes);

        nodes = modCycle.sharding(Range.greaterThan(1L));
        Assertions.assertEquals(new ExactCollection<>("t_mod_0", "t_mod_1", "t_mod_2", "t_mod_3"), nodes);
        nodes = modCycle.sharding(Range.atLeast(1L));
        Assertions.assertEquals(new ExactCollection<>("t_mod_0", "t_mod_1", "t_mod_2", "t_mod_3"), nodes);
        nodes = modCycle.sharding(Range.atLeast(3L));
        Assertions.assertEquals(new ExactCollection<>("t_mod_0", "t_mod_1", "t_mod_2", "t_mod_3"), nodes);

        nodes = modCycle.sharding(Range.lessThan(5L));
        Assertions.assertEquals(new ExactCollection<>("t_mod_0", "t_mod_1", "t_mod_2", "t_mod_3"), nodes);

        nodes = modCycle.sharding(Range.lessThan(4L));
        Assertions.assertEquals(new ExactCollection<>("t_mod_0", "t_mod_1", "t_mod_2", "t_mod_3"), nodes);

        nodes = modCycle.sharding(Range.lessThan(3L));
        Assertions.assertEquals(new ExactCollection<>("t_mod_0", "t_mod_1", "t_mod_2"), nodes);

        nodes = modCycle.sharding(Range.lessThan(2L));
        Assertions.assertEquals(new ExactCollection<>("t_mod_0", "t_mod_1"), nodes);

        nodes = modCycle.sharding(Range.atMost(2L));
        Assertions.assertEquals(new ExactCollection<>("t_mod_0", "t_mod_1", "t_mod_2"), nodes);

        nodes = modCycle.sharding(Range.atMost(3L));
        Assertions.assertEquals(new ExactCollection<>("t_mod_0", "t_mod_1", "t_mod_2", "t_mod_3"), nodes);

        nodes = modCycle.sharding(Range.atMost(4L));
        Assertions.assertEquals(new ExactCollection<>("t_mod_0", "t_mod_1", "t_mod_2", "t_mod_3"), nodes);
    }
}
