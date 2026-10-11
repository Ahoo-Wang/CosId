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
    void isIntegrityConstraintViolation() {
        assertThat(JdbcSupport.isIntegrityConstraintViolation(new SQLIntegrityConstraintViolationException("dup")), equalTo(true));
        // PostgreSQL unique_violation
        assertThat(JdbcSupport.isIntegrityConstraintViolation(new SQLException("dup", "23505")), equalTo(true));
        // MySQL / H2 / Oracle integrity constraint violation
        assertThat(JdbcSupport.isIntegrityConstraintViolation(new SQLException("dup", "23000")), equalTo(true));
        assertThat(JdbcSupport.isIntegrityConstraintViolation(new SQLException("connection failure", "08006")), equalTo(false));
        assertThat(JdbcSupport.isIntegrityConstraintViolation(new SQLException("no state")), equalTo(false));

        SQLException batch = new SQLException("batch failed", "XX000");
        batch.setNextException(new SQLException("dup", "23505"));
        assertThat(JdbcSupport.isIntegrityConstraintViolation(batch), equalTo(true));
    }
}
