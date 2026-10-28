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

package me.ahoo.cosid.migration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * The slot offset of a shared {@code cosid.generator.namespace} (api-freeze.md §2.7).
 */
class SharedSlotOffsetTest {

    @Test
    void theDefaultIsTheTopOfTheNodes() {
        // The 4.x cosid80 bits kept: 2^20 nodes, 2^10 slots at the top.
        assertThat(Migration.defaultSlotOffset(10, 20), is((1 << 20) - 1024));
        assertThat(Migration.defaultSlotOffset(9, 20), is((1 << 20) - 512));
        // Nodes beyond an int: the slots end at Integer.MAX_VALUE + 1.
        assertThat(Migration.defaultSlotOffset(10, 40), is(Integer.MAX_VALUE - 1023));
    }

    @Test
    void theWarningGivesTheHeadroom() {
        Plan.Shared wide = new Plan.Shared(3, (1 << 20) - 1024, 20, true, 1L << 20, (1 << 20) - 1028, false);
        String text = Migration.sharedWarning("global", wide, (1 << 20) - 1024, 10);
        assertThat(text, containsString("4.x can hand out " + ((1 << 20) - 1028) + " more machine ids (its hard cap is 1048576)"));
        assertThat(text, not(containsString("LOW HEADROOM")));
        Plan.Shared tight = new Plan.Shared(3, (1 << 20) - 1024, 20, true, 1L << 20, 0, false);
        assertThat(Migration.sharedWarning("global", tight, 4, 10), startsWith("LOW HEADROOM: [global]"));
        Plan.Shared atCap = new Plan.Shared(3, 1024, 11, true, 1L << 10, 1020, true);
        assertThat(Migration.sharedWarning("global", atCap, 1024, 10), containsString("4.x never reaches the 5.0 nodes"));
    }

    @Test
    void anOffsetMustBeAboveTheMachineIdsAndWithinTheNodes() {
        // M + 1 a power of two: M + 1 is the least offset, M is refused.
        Migration.checkSlotOffset("global", 3, 4, 10, 20);
        MigrationAbortedException low = assertThrows(MigrationAbortedException.class, () -> Migration.checkSlotOffset("global", 3, 3, 10, 20));
        assertThat(low.getMessage(), containsString("is not above the 4.x machine ids"));
        // The exact fit at the top passes; one more overflows.
        Migration.checkSlotOffset("global", 3, (1 << 20) - 1024, 10, 20);
        assertThrows(MigrationAbortedException.class, () -> Migration.checkSlotOffset("global", 3, (1 << 20) - 1023, 10, 20));
    }
}
