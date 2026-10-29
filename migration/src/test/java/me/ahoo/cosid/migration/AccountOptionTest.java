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

import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.migration.target.JdbcTarget;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

/**
 * The account option reaches the connection whether it is called {@code user} or {@code username} (R1-doc-05).
 */
class AccountOptionTest {
    @Test
    void theJdbcTargetConnectsAsTheUsername() {
        String url = "jdbc:h2:mem:account-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        // The first connection creates the H2 database with its admin "owner"; another account is refused.
        new JdbcTarget(StoreLocation.parse(url, Map.of("username", "owner", "password", "pw"))).close();
        new JdbcTarget(StoreLocation.parse(url, Map.of("user", "owner", "password", "pw"))).close();
        assertThrows(IllegalStateException.class, () -> new JdbcTarget(StoreLocation.parse(url, Map.of("username", "other", "password", "pw"))));
    }

    @Test
    void redisTakesTheAccountWithThePassword() {
        assertThrows(RuntimeException.class, () -> RedisConnection.open(StoreLocation.parse("redis://127.0.0.1:1",
            Map.of("username", "cosid", "password", "pw"))).close());
    }
}
