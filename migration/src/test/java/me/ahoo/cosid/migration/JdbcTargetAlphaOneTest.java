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

import me.ahoo.cosid.migration.target.JdbcTarget;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

/**
 * A 5.0.0-alpha.1 namespace table, without the {@code slot_offset} column (api-freeze.md §2.5.1).
 */
class JdbcTargetAlphaOneTest {

    private static String alphaOneStore() throws SQLException {
        String url = "jdbc:h2:mem:alpha1-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        // The 5.0 schema, then the column dropped: the table as 5.0.0-alpha.1 created it.
        new JdbcTarget(StoreLocation.parse(url, Map.of("initSchema", "true"))).close();
        try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement()) {
            statement.execute("alter table cosid_namespace drop column slot_offset");
        }
        return url;
    }

    @Test
    void offsetZeroWorksWithoutTheColumnAndAnOffsetNeedsIt() throws SQLException {
        String url = alphaOneStore();
        try (JdbcTarget target = new JdbcTarget(StoreLocation.parse(url, Map.of()))) {
            assertThat(target.prepare("plain", 4, 1000, 0, 1).slotOffset(), is(0));
            assertThat(target.namespace("plain").orElseThrow().slotOffset(), is(0));
            IllegalStateException missing = assertThrows(IllegalStateException.class, () -> target.prepare("shared", 4, 1000, 512, 1));
            assertThat(missing.getMessage(), containsString("has no slot_offset column"));
        }
    }

    @Test
    void initSchemaAddsTheColumn() throws SQLException {
        String url = alphaOneStore();
        try (JdbcTarget target = new JdbcTarget(StoreLocation.parse(url, Map.of("initSchema", "true")))) {
            assertThat(target.prepare("shared", 4, 1000, 512, 1).slotOffset(), is(512));
            assertThat(target.namespace("shared").orElseThrow().slotOffset(), is(512));
        }
    }
}
