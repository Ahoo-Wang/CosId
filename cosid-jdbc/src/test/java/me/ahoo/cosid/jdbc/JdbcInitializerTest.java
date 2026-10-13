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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;

/**
 * The initializers time out, and skip what already exists, without a database.
 */
class JdbcInitializerTest {
    
    @Test
    void segmentInitializerStatementsTimeOut() {
        FakeJdbc jdbc = new FakeJdbc(true);
        JdbcIdSegmentInitializer initializer = new JdbcIdSegmentInitializer(JdbcIdSegmentInitializer.INIT_COSID_TABLE_SQL,
            JdbcIdSegmentInitializer.INIT_ID_SEGMENT_SQL, jdbc, Duration.ofSeconds(2));
        assertThat(initializer.tryInitCosIdTable(), is(true));
        assertThat(initializer.tryInitIdSegment("ns.order", 0), is(true));
        assertThat(jdbc.queryTimeouts, contains(2, 2));
    }
    
    @Test
    void anExistingTableOrSegmentIsNotAnError() {
        FakeJdbc jdbc = new FakeJdbc(true);
        JdbcIdSegmentInitializer initializer = new JdbcIdSegmentInitializer(jdbc);
        jdbc.failure = new SQLException("Table 'cosid' already exists", "42S01", 1050);
        jdbc.failingSqlPrefix = "create table";
        assertThat(initializer.tryInitCosIdTable(), is(false));
        jdbc.failure = new SQLException("Duplicate entry", "23000", 1062);
        jdbc.failingSqlPrefix = "insert";
        assertThat(initializer.tryInitIdSegment("ns.order", 0), is(false));
        assertThat(JdbcSupport.isAlreadyExists(new SQLException("relation exists", "42P07")), is(true));
        assertThat(JdbcSupport.isAlreadyExists(new SQLException("There is already an object", "S0001", 2714)), is(true));
        assertThat(JdbcSupport.isAlreadyExists(new SQLException("syntax error", "42000", 1064)), is(false));
    }
    
    @Test
    void machineInitializerSkipsAnExistingIndexAndGoesOn() {
        FakeJdbc jdbc = new FakeJdbc(true);
        jdbc.failure = new SQLException("Duplicate key name 'idx_namespace'", "42000", 1061);
        jdbc.failingSqlPrefix = "create index idx_namespace";
        JdbcMachineIdInitializer initializer = new JdbcMachineIdInitializer(jdbc, "create table if not exists cosid_machine (name varchar(100))",
            "create index idx_namespace on cosid_machine (namespace)", "create index idx_instance_id on cosid_machine (instance_id)",
            Duration.ofSeconds(4));
        
        assertThat(initializer.tryInitCosIdMachineTable(), is(true));
        
        assertThat(jdbc.executed.size(), is(3));
        assertThat(jdbc.queryTimeouts, everyItem(is(4)));
    }
}
