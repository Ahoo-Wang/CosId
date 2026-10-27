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
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.migration.target.RangeState;
import me.ahoo.cosid.migration.target.TargetAdmin;
import me.ahoo.cosid.migration.target.Targets;
import me.ahoo.cosid.store.Store;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** {@code purge} (D23): only sealed period keys of earlier periods go. */
class PurgeTest {
    @ParameterizedTest
    @EnumSource(TestBackend.class)
    void deletesSealedEarlierPeriodsOnly(TestBackend backend) {
        TestBackend.Store store = backend.store();
        String ns = "p" + UUID.randomUUID().toString().substring(0, 8);
        try (TargetAdmin admin = Targets.open(store.location()); Store store5 = store.open5()) {
            for (String key : List.of("bill/20230101", "bill/20240101", "bill/20240102", "bill/2024", "order", "bill/20260101")) {
                admin.raise(ns + "/" + key, 10);
            }
            for (String key : List.of("bill/20230101", "bill/20240101", "bill/2024", "order", "bill/20260101")) {
                store5.ranges().seal(ns + "/" + key);
            }
        }
        Migration migration = new Migration(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        StoreLocation location = store.location();
        assertThrows(IllegalArgumentException.class, () -> migration.purge(new Migration.PurgeOptions(location, ns, "2025-01", true, false)));
        assertThrows(MigrationAbortedException.class, () -> migration.purge(new Migration.PurgeOptions(location, ns, "20250101", false, false)));
        List<String> expected = List.of(ns + "/bill/20230101", ns + "/bill/20240101");
        assertThat(migration.purge(new Migration.PurgeOptions(location, ns, "20250101", false, true)), is(expected));
        assertThat(migration.purge(new Migration.PurgeOptions(location, ns, "20250101", true, false)), is(expected));
        try (TargetAdmin admin = Targets.open(location)) {
            assertThat(admin.ranges(ns + "/").stream().map(RangeState::key).toList(),
                is(List.of(ns + "/bill/2024", ns + "/bill/20240102", ns + "/bill/20260101", ns + "/order")));
        }
    }
}
