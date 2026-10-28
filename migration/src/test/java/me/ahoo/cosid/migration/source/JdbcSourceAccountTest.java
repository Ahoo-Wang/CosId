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

package me.ahoo.cosid.migration.source;

import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.migration.StoreLocation;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

/**
 * The 4.x JDBC source connects with {@code user} or {@code username} (R1-doc-05).
 */
class JdbcSourceAccountTest {
    @Test
    void connectsAsTheUsername() throws SQLException {
        String url = "jdbc:h2:mem:source-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (Connection first = JdbcSource.connect(StoreLocation.parse(url, Map.of("username", "owner", "password", "pw")))) {
            first.isValid(1);
        }
        assertThrows(SQLException.class, () -> JdbcSource.connect(StoreLocation.parse(url, Map.of("username", "other", "password", "pw"))).close());
    }
}
