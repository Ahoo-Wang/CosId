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

package me.ahoo.cosid.store.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Slot store behaviour the contract does not reach, on H2.
 */
class JdbcSlotLeaseStoreTest {
    @Test
    void createsTheSlotRowsAFirstUserLeftMissing() throws SQLException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:slots-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        JdbcStore store = new JdbcStore(dataSource, JdbcDialects.H2, JdbcTables.DEFAULT, Duration.ofSeconds(5));
        store.initSchema();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement("insert into cosid_namespace (namespace, slot_limit, tolerance_ms) values ('orphan', 4, 1000)")) {
            insert.executeUpdate();
        }
        AcquireResult result = store.slots().acquire("orphan", new Owner("host", UUID.randomUUID()), 4, Duration.ofSeconds(30), Duration.ofSeconds(1),
            OptionalInt.empty());
        assertEquals(0, assertInstanceOf(AcquireResult.Acquired.class, result).lease().slot(), "the waiting user created the rows itself");
        try (Connection connection = dataSource.getConnection();
             ResultSet rs = connection.createStatement().executeQuery("select count(*) from cosid_slot where namespace = 'orphan'")) {
            rs.next();
            assertEquals(4, rs.getInt(1));
        }
    }
}
