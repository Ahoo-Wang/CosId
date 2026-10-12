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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.lease.Lease;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Duration;

/**
 * Cases of {@link JdbcMachineLeaseStore} and {@link JdbcSupport} beyond the shared spec, on a real MySQL: the insert
 * retry of a new machine id and the statement timeouts.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcMachineLeaseStoreMySqlTest {
    private static final String INSERT_MACHINE = "insert into cosid_machine";
    private static final InstanceId INSTANCE_ID = InstanceId.of("host", 1, false);
    private static final long NOW = 1_000_000L;
    
    DataSource realDataSource;
    RecordingDataSource dataSource;
    String namespace;
    
    @BeforeAll
    void setupDataSource() {
        realDataSource = RealMySql.createDataSource();
    }
    
    @BeforeEach
    void setup() {
        dataSource = new RecordingDataSource(realDataSource);
        namespace = MockIdGenerator.INSTANCE.generateAsString();
    }
    
    private JdbcMachineLeaseStore store(Duration queryTimeout) {
        return new JdbcMachineLeaseStore(dataSource, queryTimeout, JdbcMachineLeaseStore.MYSQL_SERVER_TIME_SQL);
    }
    
    private Lease claimNew() {
        return store(JdbcSupport.DEFAULT_QUERY_TIMEOUT).claimNew(namespace, INSTANCE_ID, 15, NOW).orElseThrow();
    }
    
    @Test
    void claimNewRetriesWhenDuplicateIsReportedBySqlStateClass23() {
        dataSource.failExecutions(INSERT_MACHINE, "23505", 0, 1);
        
        assertThat(claimNew().getMachineId(), equalTo(0));
        assertThat(dataSource.getAttempts(), equalTo(2));
    }
    
    @Test
    void claimNewDoesNotRetryOnOtherSqlState() {
        dataSource.failExecutions(INSERT_MACHINE, "08006", 0, 1);
        
        CosIdException actual = Assertions.assertThrows(CosIdException.class, this::claimNew);
        
        assertThat(((SQLException) actual.getCause()).getSQLState(), equalTo("08006"));
        assertThat(dataSource.getAttempts(), equalTo(1));
    }
    
    @Test
    void claimNewDoesNotRetryOnNonDuplicateIntegrityViolation() {
        // MySQL reports NOT NULL violations as 23000 too; only 1062 is a duplicate key.
        dataSource.failExecutions(INSERT_MACHINE, "23000", 1048, 1);
        
        Assertions.assertThrows(CosIdException.class, this::claimNew);
        
        assertThat(dataSource.getAttempts(), equalTo(1));
    }
    
    @Test
    void claimNewRetriesMySqlDuplicate() {
        dataSource.failExecutions(INSERT_MACHINE, "23000", 1062, 3);
        
        assertThat(claimNew().getMachineId(), equalTo(0));
        assertThat(dataSource.getAttempts(), equalTo(4));
    }
    
    @Test
    void claimNewStopsRetryingAfterMaxAttempts() {
        dataSource.failExecutions(INSERT_MACHINE, "23505", 0, Integer.MAX_VALUE);
        
        CosIdException actual = Assertions.assertThrows(CosIdException.class, this::claimNew);
        
        assertThat(((SQLException) actual.getCause()).getSQLState(), equalTo("23505"));
        assertThat(dataSource.getAttempts(), equalTo(JdbcMachineLeaseStore.MAX_ATTEMPTS));
    }
    
    @Test
    void statementsUseQueryTimeoutRoundedUpToSeconds() {
        store(Duration.ofMillis(1500)).claimNew(namespace, INSTANCE_ID, 15, NOW);
        
        assertThat(dataSource.getQueryTimeouts(), not(empty()));
        assertThat(dataSource.getQueryTimeouts(), everyItem(equalTo(2)));
    }
    
    @Test
    void statementsUseDefaultQueryTimeout() {
        claimNew();
        
        assertThat(dataSource.getQueryTimeouts(), not(empty()));
        assertThat(dataSource.getQueryTimeouts(), everyItem(equalTo(5)));
    }
    
    @Test
    void zeroQueryTimeoutSetsNoTimeout() {
        store(Duration.ZERO).claimNew(namespace, INSTANCE_ID, 15, NOW);
        
        assertThat(dataSource.getQueryTimeouts(), empty());
    }
}
