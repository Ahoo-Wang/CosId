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

import me.ahoo.cosid.migration.config.FlatConfig;
import me.ahoo.cosid.migration.config.Legacy4x;
import me.ahoo.cosid.time.Layout;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class Legacy4xTest {
    @Test
    void aSecondsEpochIsInSeconds() {
        Legacy4x legacy = new Legacy4x(FlatConfig.ofYaml("""
            cosid:
              snowflake:
                enabled: true
                share: { timestamp-unit: second }
                provider:
                  custom: { timestamp-unit: second, epoch: 1600000000 }
                  millis: { epoch: 1600000000000 }
            """));
        // 4.x reads the default epoch as its seconds twin, any other epoch in the id's unit (rc-review D-M3).
        assertThat(legacy.snowflakeLayout("cosid.snowflake.share").epoch(), is(Layout.COSID_EPOCH));
        assertThat(legacy.snowflakeLayout("cosid.snowflake.provider.custom").epoch(), is(Instant.ofEpochSecond(1_600_000_000L)));
        assertThat(legacy.snowflakeLayout("cosid.snowflake.provider.millis").epoch(), is(Instant.ofEpochMilli(1_600_000_000_000L)));
    }

    @Test
    void namespacesAndStores() {
        Legacy4x legacy = new Legacy4x(FlatConfig.ofYaml("""
            cosid:
              namespace: shop
              machine: { enabled: true, distributor: { type: jdbc } }
              snowflake:
                enabled: true
                provider: { trace: { namespace: tracing } }
              segment:
                enabled: true
                share: { namespace: legacy }
                provider:
                  order: { namespace: legacy }
                  bill: { namespace: shop }
            """));
        assertThat(legacy.extraNamespaces(), is(Map.of("legacy", Set.of("__share__", "order"), "tracing", Set.of())));
        assertThat(legacy.segmentStoreType(), is(Optional.of("redis")));
        assertThat(legacy.machineStoreType(), is(Optional.of("jdbc")));
        assertThat(new Legacy4x(FlatConfig.ofYaml("cosid: { machine: { enabled: true } }")).machineStoreType(), is(Optional.empty()));
        assertThat(new Legacy4x(FlatConfig.ofYaml("cosid: { namespace: x }")).segmentStoreType(), is(Optional.empty()));
    }
}
