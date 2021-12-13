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

package me.ahoo.cosid.shardingsphere.sharding.mod;

import com.google.common.collect.Range;
import me.ahoo.cosid.shardingsphere.sharding.utils.ExactCollection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;

/**
 * @author ahoo wang
 */
class ModCycleTest {
    public static ModCycle createModCycle() {
        return new ModCycle(10, "t_mod_");
    }


    @Test
    public void test() {
        ModCycle modCycle = createModCycle();
        Assertions.assertNotNull(modCycle);
        Assertions.assertEquals(10, modCycle.getDivisor());
    }

    @Test
    public void shardingPrecise() {
        ModCycle modCycle = createModCycle();
        String node = modCycle.sharding(1L);
        Assertions.assertEquals("t_mod_1", node);
        node = modCycle.sharding(2L);
        Assertions.assertEquals("t_mod_2", node);
        node = modCycle.sharding(3L);
        Assertions.assertEquals("t_mod_3", node);
        node = modCycle.sharding(4L);
        Assertions.assertEquals("t_mod_4", node);
        node = modCycle.sharding(5L);
        Assertions.assertEquals("t_mod_5", node);
        node = modCycle.sharding(6L);
        Assertions.assertEquals("t_mod_6", node);
        node = modCycle.sharding(7L);
        Assertions.assertEquals("t_mod_7", node);
        node = modCycle.sharding(8L);
        Assertions.assertEquals("t_mod_8", node);
        node = modCycle.sharding(9L);
        Assertions.assertEquals("t_mod_9", node);
        node = modCycle.sharding(10L);
        Assertions.assertEquals("t_mod_0", node);
        node = modCycle.sharding(11L);
        Assertions.assertEquals("t_mod_1", node);
    }

    @Test
    public void shardingRange() {
        ModCycle modCycle = createModCycle();
        Collection<String> nodes = modCycle.sharding(Range.closed(1L, 3L));
        Assertions.assertEquals(new ExactCollection<>("t_mod_1", "t_mod_2", "t_mod_3"), nodes);
        nodes = modCycle.sharding(Range.closedOpen(1L, 3L));
        Assertions.assertEquals(new ExactCollection<>("t_mod_1", "t_mod_2"), nodes);
        nodes = modCycle.sharding(Range.openClosed(1L, 3L));

        Assertions.assertEquals(new ExactCollection<>("t_mod_2", "t_mod_3"), nodes);
        nodes = modCycle.sharding(Range.open(1L, 3L));
        Assertions.assertEquals(new ExactCollection<>("t_mod_2"), nodes);

        nodes = modCycle.sharding(Range.greaterThan(1L));
        Assertions.assertEquals(new ExactCollection<>("t_mod_0", "t_mod_1", "t_mod_2", "t_mod_3", "t_mod_4", "t_mod_5", "t_mod_6", "t_mod_7", "t_mod_8", "t_mod_9"), nodes);
        nodes = modCycle.sharding(Range.atLeast(1L));
        Assertions.assertEquals(new ExactCollection<>("t_mod_0", "t_mod_1", "t_mod_2", "t_mod_3", "t_mod_4", "t_mod_5", "t_mod_6", "t_mod_7", "t_mod_8", "t_mod_9"), nodes);

        nodes = modCycle.sharding(Range.lessThan(3L));
        Assertions.assertEquals(new ExactCollection<>("t_mod_0", "t_mod_1", "t_mod_2", "t_mod_3", "t_mod_4", "t_mod_5", "t_mod_6", "t_mod_7", "t_mod_8", "t_mod_9"), nodes);
        nodes = modCycle.sharding(Range.atLeast(3L));
        Assertions.assertEquals(new ExactCollection<>("t_mod_0", "t_mod_1", "t_mod_2", "t_mod_3", "t_mod_4", "t_mod_5", "t_mod_6", "t_mod_7", "t_mod_8", "t_mod_9"), nodes);
    }
}
