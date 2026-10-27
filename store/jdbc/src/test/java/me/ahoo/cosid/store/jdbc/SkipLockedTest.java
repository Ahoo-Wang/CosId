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
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import me.ahoo.cosid.lease.AcquireReason;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLeaseStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * The lock hints of the slot choice (06-stores.md §2.1) on a real database: a candidate row another transaction holds
 * locked is skipped ({@code for update skip locked}; SQL Server {@code with (updlock, readpast, rowlock)}), so an
 * acquire takes the next free slot instead of waiting for the lock. Without the hint the acquire would wait for the
 * lock timeout of the database, far beyond the bound of these cases.
 */
abstract class SkipLockedTest {
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final Duration TOLERANCE = Duration.ofSeconds(1);
    private static final Duration BOUND = Duration.ofSeconds(5);

    abstract TestDatabase database();

    /**
     * Locks one slot row the way a concurrent acquirer does, in the open transaction of {@code connection}.
     */
    String lockStatement() {
        return "select slot from cosid_slot where namespace = ? and slot = ? for update";
    }

    @BeforeEach
    void available() {
        database().assumeAvailable();
    }

    private static Owner owner(String key) {
        return new Owner(key, UUID.randomUUID());
    }

    private void lock(Connection connection, String namespace, int slot) throws SQLException {
        connection.setAutoCommit(false);
        try (PreparedStatement select = connection.prepareStatement(lockStatement())) {
            select.setString(1, namespace);
            select.setInt(2, slot);
            try (ResultSet rs = select.executeQuery()) {
                assertEquals(true, rs.next(), "the row to lock exists");
            }
        }
    }

    @Test
    void detectsTheDialectOfTheDatabase() {
        assertEquals(database().dialect(), JdbcStore.of(database().dataSource(), TestDatabase.TIMEOUT).dialect());
    }

    @Test
    void lowestStepSkipsALockedFreeSlot() throws SQLException {
        String namespace = "skip-" + UUID.randomUUID();
        SlotLeaseStore store = database().store().slots();
        assertEquals(0, assertInstanceOf(AcquireResult.Acquired.class, store.acquire(namespace, owner("host-0"), 4, TTL, TOLERANCE, OptionalInt.empty()))
            .lease().slot());
        try (Connection connection = database().dataSource().getConnection()) {
            lock(connection, namespace, 1);
            try {
                AcquireResult.Acquired next = assertTimeoutPreemptively(BOUND,
                    () -> assertInstanceOf(AcquireResult.Acquired.class, store.acquire(namespace, owner("host-1"), 4, TTL, TOLERANCE, OptionalInt.empty())));
                assertEquals(2, next.lease().slot(), "slot 1 is free but locked: passed over");
                assertEquals(AcquireReason.LOWEST, next.reason());
            } finally {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        }
    }

    @Test
    void preferredStepSkipsALockedPreferredSlot() throws SQLException {
        String namespace = "skip-" + UUID.randomUUID();
        SlotLeaseStore store = database().store().slots();
        assertInstanceOf(AcquireResult.Acquired.class, store.acquire(namespace, owner("host-0"), 4, TTL, TOLERANCE, OptionalInt.empty()));
        try (Connection connection = database().dataSource().getConnection()) {
            lock(connection, namespace, 3);
            try {
                AcquireResult.Acquired next = assertTimeoutPreemptively(BOUND,
                    () -> assertInstanceOf(AcquireResult.Acquired.class, store.acquire(namespace, owner("host-1"), 4, TTL, TOLERANCE, OptionalInt.of(3))));
                assertEquals(1, next.lease().slot(), "the locked preferred slot 3 is passed over for the lowest free one");
                assertEquals(AcquireReason.LOWEST, next.reason());
            } finally {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        }
    }
}
