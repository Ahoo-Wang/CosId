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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * The slot offset of a shared {@code cosid.generator.namespace} (api-freeze.md §2.7).
 */
class SharedSlotOffsetTest {

    @Test
    void theDefaultIsTheHardCapOfThe4xDistributor() {
        // With node bits to spare, 2^(4.x cosid.generator.machine-bit): 4.x never hands out a machine id that high.
        assertThat(Migration.sharedSlotOffset("global", 3, null, 10, 1 << 10, 20), is(1024));
        // The exact fit: cap + 2^bits = 2^nodeBits.
        assertThat(Migration.sharedSlotOffset("global", 1023, null, 10, 1 << 10, 11), is(1024));
    }

    @Test
    void anExplicitOffsetIsRequiredWhenTheCapLeavesNoRoom() {
        // The 4.x bits kept: the cap 2^20 fills the 2^20 nodes.
        MigrationAbortedException implicit = assertThrows(MigrationAbortedException.class,
            () -> Migration.sharedSlotOffset("global", 3, null, 10, 1 << 20, 20));
        assertThat(implicit.getMessage(), containsString("no slot offset is safe by construction"));
        assertThat(implicit.getMessage(), containsString("Take an explicit --slot-offset"));
        // M + 1 a power of two: M + 1 is the least offset, M is refused.
        assertThat(Migration.sharedSlotOffset("global", 3, 4, 10, 1 << 20, 20), is(4));
        MigrationAbortedException low = assertThrows(MigrationAbortedException.class,
            () -> Migration.sharedSlotOffset("global", 3, 3, 10, 1 << 20, 20));
        assertThat(low.getMessage(), containsString("is not above the 4.x machine ids"));
        // Still within the nodes.
        assertThrows(MigrationAbortedException.class, () -> Migration.sharedSlotOffset("global", 3, (1 << 20) - 512, 10, 1 << 20, 20));
    }
}
