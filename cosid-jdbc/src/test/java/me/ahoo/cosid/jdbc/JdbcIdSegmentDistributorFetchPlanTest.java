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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.CosIdException;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;

/**
 * Round-trips and auto-commit of a segment fetch, without a database.
 */
class JdbcIdSegmentDistributorFetchPlanTest {
    
    private static JdbcIdSegmentDistributor distributor(FakeJdbc jdbc, String incrementSql, String fetchSql) {
        return new JdbcIdSegmentDistributor("ns", "order", 10, incrementSql, fetchSql, jdbc, Duration.ofSeconds(3));
    }
    
    @Test
    void theDefaultMySqlStatementsTakeOneStatementWithoutATransaction() {
        FakeJdbc jdbc = new FakeJdbc(true);
        JdbcIdSegmentDistributor distributor = distributor(jdbc, JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL, JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL);
        assertThat(distributor.getFetchPlan(), is(JdbcIdSegmentDistributor.FetchPlan.LAST_INSERT_ID));
        
        assertThat(distributor.nextMaxId(10), equalTo(42L));
        
        assertThat(jdbc.executed, contains(JdbcIdSegmentDistributor.LAST_INSERT_ID_INCREMENT_MAX_ID_SQL));
        assertThat(jdbc.events, contains("close:autoCommit=true"));
        assertThat(jdbc.queryTimeouts, everyItem(is(3)));
    }
    
    @Test
    void thePostgreSqlStatementsTakeOneStatement() {
        FakeJdbc jdbc = new FakeJdbc(true);
        JdbcIdSegmentDistributor distributor = distributor(jdbc, PostgreSqlDialect.INCREMENT_MAX_ID_SQL, PostgreSqlDialect.FETCH_MAX_ID_SQL);
        assertThat(distributor.getFetchPlan(), is(JdbcIdSegmentDistributor.FetchPlan.RETURNING));
        
        assertThat(distributor.nextMaxId(10), equalTo(42L));
        
        assertThat(jdbc.executed, contains(PostgreSqlDialect.INCREMENT_AND_FETCH_MAX_ID_SQL));
    }
    
    @Test
    void aManualCommitConnectionGoesBackInManualCommit() {
        FakeJdbc jdbc = new FakeJdbc(false);
        distributor(jdbc, JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL, JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL).nextMaxId(10);
        assertThat(jdbc.events, contains("autoCommit=true", "autoCommit=false", "close:autoCommit=false"));
    }
    
    @Test
    void otherStatementsRunInATransactionAndRestoreAutoCommit() {
        FakeJdbc jdbc = new FakeJdbc(true);
        String increment = "update my_cosid set last_max_id=(last_max_id + ?) where name = ?";
        String fetch = "select last_max_id from my_cosid where name = ?";
        JdbcIdSegmentDistributor distributor = distributor(jdbc, increment, fetch);
        assertThat(distributor.getFetchPlan(), is(JdbcIdSegmentDistributor.FetchPlan.TRANSACTION));
        
        assertThat(distributor.nextMaxId(10), equalTo(42L));
        
        assertThat(jdbc.executed, contains(increment, fetch));
        assertThat(jdbc.events, contains("autoCommit=false", "commit", "autoCommit=true", "close:autoCommit=true"));
    }
    
    @Test
    void aFailedTransactionRollsBackAndRestoresAutoCommit() {
        FakeJdbc jdbc = new FakeJdbc(true);
        jdbc.failure = new SQLException("down", "08006");
        jdbc.failingSqlPrefix = "select";
        JdbcIdSegmentDistributor distributor = distributor(jdbc, "update my_cosid set last_max_id=(last_max_id + ?) where name = ?",
            "select last_max_id from my_cosid where name = ?");
        
        assertThrows(CosIdException.class, () -> distributor.nextMaxId(10));
        
        assertThat(jdbc.events, contains("autoCommit=false", "rollback", "autoCommit=true", "close:autoCommit=true"));
    }
    
    @Test
    void theBuiltInStatementsAreRecognizedAsConfiguredInYaml() {
        assertThat(JdbcIdSegmentDistributor.FetchPlan.of(
            "update cosid set last_max_id=(last_max_id + ?), last_fetch_time=cast(extract(epoch from now()) as bigint) where name = ?",
            "select last_max_id from cosid where name = ?;"), is(JdbcIdSegmentDistributor.FetchPlan.RETURNING));
        assertThat(JdbcIdSegmentDistributor.FetchPlan.of(JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL, PostgreSqlDialect.FETCH_MAX_ID_SQL),
            is(JdbcIdSegmentDistributor.FetchPlan.LAST_INSERT_ID));
        assertThat(JdbcIdSegmentDistributor.FetchPlan.of(JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL, "select max_id from other where name = ?"),
            is(JdbcIdSegmentDistributor.FetchPlan.TRANSACTION));
    }
    
    @Test
    void aGeneratedKeyBelowTheStepIsRejected() {
        FakeJdbc jdbc = new FakeJdbc(true);
        // Not the update's last_insert_id(expr): a proxy that answered for another session.
        jdbc.maxId = 0;
        JdbcIdSegmentDistributor distributor = distributor(jdbc, JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL, JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL);
        
        CosIdException exception = assertThrows(CosIdException.class, () -> distributor.nextMaxId(10));
        
        assertThat(exception.getMessage().contains("fetch-plan=transaction"), is(true));
    }
    
    @Test
    void theMaxIdIsTheUpdatesOwnGeneratedKeyNotALaterSelect() {
        FakeJdbc jdbc = new FakeJdbc(true);
        // A middleware that routes a "select last_insert_id()" to another backend connection would answer 99 there.
        jdbc.maxId = 99;
        jdbc.generatedKey = 42L;
        JdbcIdSegmentDistributor distributor = distributor(jdbc, JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL, JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL);
        
        assertThat(distributor.nextMaxId(10), equalTo(42L));
        assertThat(jdbc.executed, contains(JdbcIdSegmentDistributor.LAST_INSERT_ID_INCREMENT_MAX_ID_SQL));
    }
    
    @Test
    void noGeneratedKeyIsAFailureNotAFallback() {
        FakeJdbc jdbc = new FakeJdbc(true);
        jdbc.generatedKeys = false;
        JdbcIdSegmentDistributor distributor = distributor(jdbc, JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL, JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL);
        
        CosIdException exception = assertThrows(CosIdException.class, () -> distributor.nextMaxId(10));
        
        assertThat(exception.getMessage().contains("fetch-plan=transaction"), is(true));
        assertThat(jdbc.executed, contains(JdbcIdSegmentDistributor.LAST_INSERT_ID_INCREMENT_MAX_ID_SQL));
    }
    
    @Test
    void theTransactionModeKeepsTheTransactionForTheBuiltInStatements() {
        FakeJdbc jdbc = new FakeJdbc(true);
        JdbcIdSegmentDistributor distributor = new JdbcIdSegmentDistributor("ns", "order", 10, JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL,
            JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL, jdbc, Duration.ofSeconds(3), JdbcIdSegmentDistributor.FetchMode.TRANSACTION);
        assertThat(distributor.getFetchPlan(), is(JdbcIdSegmentDistributor.FetchPlan.TRANSACTION));
        
        distributor.nextMaxId(10);
        
        assertThat(jdbc.executed, contains(JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL, JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL));
        assertThat(jdbc.events, contains("autoCommit=false", "commit", "autoCommit=true", "close:autoCommit=true"));
    }
    
    @Test
    void anotherCaseIsAnotherStatement() {
        // On a case-sensitive MySQL COSID is not the cosid table: the user's statements must run as they are.
        assertThat(JdbcIdSegmentDistributor.FetchPlan.of(JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL.replace("cosid", "COSID"),
            JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL.replace("cosid", "COSID")), is(JdbcIdSegmentDistributor.FetchPlan.TRANSACTION));
    }
}
