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

import me.ahoo.cosid.RangeExhaustedException;
import me.ahoo.cosid.RangeSealedException;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.segment.RangeStore;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Ranges in {@code cosid_range} (06-stores.md §2.1): one {@link JdbcDialect#reserve} per reservation, the result
 * {@code [next − size, next)}. A sealed key keeps its row and value (S6); no path deletes a key (D23).
 */
final class JdbcRangeStore implements RangeStore {
    private final Jdbc jdbc;

    JdbcRangeStore(Jdbc jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Range reserve(String key, long size, long initialOffset) {
        Objects.requireNonNull(key, "key");
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive: " + size);
        }
        if (initialOffset < 0) {
            throw new IllegalArgumentException("initialOffset must not be negative: " + initialOffset);
        }
        jdbc.dialect.checkKey("range key", key);
        return jdbc.run("reserve " + key, session -> {
            long offset = initialOffset;
            if (offset > Long.MAX_VALUE - size) {
                // The insert branch would overflow: only an existing key, whose initialOffset no longer applies, can reserve.
                if (row(session, key) == Row.NONE) {
                    throw new RangeExhaustedException(key);
                }
                offset = 0;
            }
            long start = offset;
            OptionalLong next = jdbc.retryingDuplicates(() -> jdbc.dialect.reserve(session, key, size, start));
            if (next.isEmpty()) {
                if (row(session, key) == Row.SEALED) {
                    throw new RangeSealedException(key);
                }
                throw new RangeExhaustedException(key);
            }
            return new Range(next.getAsLong() - size, next.getAsLong());
        });
    }

    /**
     * Seals the key; a key that does not exist yet is created sealed at 0, so it is never created by a reserve (S6).
     *
     * @param key the range key
     * @return true
     */
    @Override
    public boolean seal(String key) {
        Objects.requireNonNull(key, "key");
        jdbc.dialect.checkKey("range key", key);
        return jdbc.run("seal " + key, session -> {
            if (sealExisting(session, key) > 0) {
                return true;
            }
            try (PreparedStatement insert = session.prepare("insert into " + session.tables().range() + " (range_key, next_value, sealed) values (?, 0, ?)")) {
                insert.setString(1, key);
                insert.setBoolean(2, true);
                insert.executeUpdate();
            } catch (SQLException e) {
                if (!jdbc.dialect.isDuplicateKey(e)) {
                    throw e;
                }
                // Created meanwhile; rows are never deleted, so this update finds it.
                sealExisting(session, key);
            }
            return true;
        });
    }

    private static int sealExisting(JdbcSession session, String key) throws SQLException {
        try (PreparedStatement update = session.prepare("update " + session.tables().range() + " set sealed = ? where range_key = ?")) {
            update.setBoolean(1, true);
            update.setString(2, key);
            return update.executeUpdate();
        }
    }

    private static Row row(JdbcSession session, String key) throws SQLException {
        try (PreparedStatement select = session.prepare("select sealed from " + session.tables().range() + " where range_key = ?")) {
            select.setString(1, key);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return Row.NONE;
                }
                return rs.getBoolean(1) ? Row.SEALED : Row.OPEN;
            }
        }
    }

    private enum Row {
        NONE, OPEN, SEALED
    }
}
