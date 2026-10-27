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

import org.junit.jupiter.api.Test;

import java.util.Map;

class StoreLocationTest {
    @Test
    void theSchemeGivesTheType() {
        assertThat(StoreLocation.parse("jdbc:mysql://db/cosid", Map.of()).type(), is(StoreLocation.JDBC));
        assertThat(StoreLocation.parse("redis://localhost:6379", Map.of()).type(), is(StoreLocation.REDIS));
        assertThat(StoreLocation.parse("rediss://localhost:6379", Map.of()).type(), is(StoreLocation.REDIS));
        assertThat(StoreLocation.parse("mongodb+srv://cluster/", Map.of()).type(), is(StoreLocation.MONGO));
        assertThat(StoreLocation.parse("zookeeper://zk:2181", Map.of()).type(), is(StoreLocation.ZOOKEEPER));
        assertThrows(IllegalArgumentException.class, () -> StoreLocation.parse("http://localhost", Map.of()));
    }

    @Test
    void passwordsStayOutOfThePlan() {
        StoreLocation location = StoreLocation.parse("jdbc:h2:mem:x", Map.of("user", "sa", "password", "secret"));
        assertThat(location.redacted().options(), is(Map.of("user", "sa")));
        assertThat(location.withOptions(Map.of("user", "root")).option("user"), is("root"));
        assertThat(location.withOptions(Map.of()), is(location));
        assertThat(location.withEnvironmentPassword("FROM").option("password"), is("secret"));
        assertThat(location.toString(), is("jdbc jdbc:h2:mem:x"));
    }
}
