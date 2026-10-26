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

package me.ahoo.cosid.jdbc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Duration;

class JdbcSupportTest {

    @Test
    void toQueryTimeoutSeconds() {
        assertThat(JdbcSupport.toQueryTimeoutSeconds(null), equalTo(0));
        assertThat(JdbcSupport.toQueryTimeoutSeconds(Duration.ZERO), equalTo(0));
        assertThat(JdbcSupport.toQueryTimeoutSeconds(Duration.ofMillis(1)), equalTo(1));
        assertThat(JdbcSupport.toQueryTimeoutSeconds(Duration.ofSeconds(1)), equalTo(1));
        assertThat(JdbcSupport.toQueryTimeoutSeconds(Duration.ofMillis(1001)), equalTo(2));
        assertThat(JdbcSupport.toQueryTimeoutSeconds(Duration.ofDays(100_000)), equalTo(Integer.MAX_VALUE));
        Assertions.assertThrows(IllegalArgumentException.class, () -> JdbcSupport.toQueryTimeoutSeconds(Duration.ofSeconds(-1)));
    }

    @Test
    void isDuplicateKey() {
        // PostgreSQL / H2 unique_violation
        assertThat(JdbcSupport.isDuplicateKey(new SQLException("dup", "23505")), equalTo(true));
        // MySQL duplicate entry
        assertThat(JdbcSupport.isDuplicateKey(new SQLIntegrityConstraintViolationException("dup", "23000", 1062)), equalTo(true));
        // Oracle ORA-00001, SQL Server 2627 / 2601
        assertThat(JdbcSupport.isDuplicateKey(new SQLException("dup", "23000", 1)), equalTo(true));
        assertThat(JdbcSupport.isDuplicateKey(new SQLException("dup", "23000", 2627)), equalTo(true));
        assertThat(JdbcSupport.isDuplicateKey(new SQLException("dup", "23000", 2601)), equalTo(true));
        // Driver without SQLState
        assertThat(JdbcSupport.isDuplicateKey(new SQLIntegrityConstraintViolationException("dup")), equalTo(true));

        // MySQL NOT NULL (1048) and foreign key (1452) violations, PostgreSQL not_null_violation
        assertThat(JdbcSupport.isDuplicateKey(new SQLIntegrityConstraintViolationException("null", "23000", 1048)), equalTo(false));
        assertThat(JdbcSupport.isDuplicateKey(new SQLIntegrityConstraintViolationException("fk", "23000", 1452)), equalTo(false));
        assertThat(JdbcSupport.isDuplicateKey(new SQLException("null", "23502")), equalTo(false));
        assertThat(JdbcSupport.isDuplicateKey(new SQLException("connection failure", "08006")), equalTo(false));
        assertThat(JdbcSupport.isDuplicateKey(new SQLException("no state")), equalTo(false));

        SQLException batch = new SQLException("batch failed", "XX000");
        batch.setNextException(new SQLException("dup", "23505"));
        assertThat(JdbcSupport.isDuplicateKey(batch), equalTo(true));
    }

    @Test
    void isDeadlock() {
        // SQL Server 1205, MySQL/MariaDB 1213, PostgreSQL serialization failure
        assertThat(JdbcSupport.isDeadlock(new SQLException("victim", "40001", 1205)), equalTo(true));
        assertThat(JdbcSupport.isDeadlock(new SQLException("deadlock", "40001", 1213)), equalTo(true));
        assertThat(JdbcSupport.isDeadlock(new SQLException("serialization", "40001")), equalTo(true));
        // PostgreSQL deadlock_detected
        assertThat(JdbcSupport.isDeadlock(new SQLException("deadlock", "40P01")), equalTo(true));
        // SQL Server 1205 with xopenStates=true
        assertThat(JdbcSupport.isDeadlock(new SQLException("victim", "42000", 1205)), equalTo(true));

        // MySQL lock wait timeout (1205): the statement waited, the transaction is kept
        assertThat(JdbcSupport.isDeadlock(new SQLException("lock wait", "HY000", 1205)), equalTo(false));
        assertThat(JdbcSupport.isDeadlock(new SQLException("syntax", "42000", 102)), equalTo(false));
        assertThat(JdbcSupport.isDeadlock(new SQLException("connection failure", "08006")), equalTo(false));
        assertThat(JdbcSupport.isDeadlock(new SQLException("no state")), equalTo(false));

        SQLException batch = new SQLException("batch failed", "XX000");
        batch.setNextException(new SQLException("deadlock", "40P01"));
        assertThat(JdbcSupport.isDeadlock(batch), equalTo(true));
    }
}
