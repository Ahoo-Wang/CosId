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
import static org.hamcrest.Matchers.is;

import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.Optional;

class JdbcDialectTest {
    
    @ParameterizedTest
    @CsvSource({
        "MySQL, MYSQL",
        "MariaDB, MYSQL",
        "PostgreSQL, POSTGRESQL",
        "H2, H2",
        "Microsoft SQL Server, SQL_SERVER"
    })
    void ofProductName(String product, JdbcDialect expected) {
        assertThat(JdbcDialect.of(product), equalTo(Optional.of(expected)));
    }
    
    @ParameterizedTest
    @CsvSource({"Oracle", "DB2", "SQLite", "''"})
    void unsupportedProducts(String product) {
        assertThat(JdbcDialect.of(product), equalTo(Optional.empty()));
        assertThat(JdbcDialect.of((String) null), equalTo(Optional.empty()));
    }
    
    @Test
    void ofDataSource() throws Exception {
        assertThat(JdbcDialect.of(RealH2.createDataSource()), equalTo(Optional.of(JdbcDialect.H2)));
        assertThat(JdbcDialect.of(RealH2.createDataSource(";MODE=MySQL")), equalTo(Optional.of(JdbcDialect.H2)));
    }
    
    @Test
    void mysqlIsTheDefaultOfEveryClass() {
        assertThat(JdbcDialect.MYSQL.getIncrementMaxIdSql(), equalTo(JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL));
        assertThat(JdbcDialect.MYSQL.getInitCosIdTableSql(), equalTo(JdbcIdSegmentInitializer.INIT_COSID_TABLE_SQL));
        assertThat(JdbcDialect.MYSQL.getInitCosIdMachineTableSql(), equalTo(JdbcMachineIdInitializer.INIT_COSID_MACHINE_TABLE_SQL));
        assertThat(JdbcDialect.MYSQL.getServerTimeSql(), equalTo(JdbcMachineLeaseStore.MYSQL_SERVER_TIME_SQL));
    }
    
    @ParameterizedTest
    @EnumSource(value = JdbcDialect.class, names = {"POSTGRESQL", "H2", "SQL_SERVER"})
    void theBuiltInStatementsTakeOneQuery(JdbcDialect dialect) {
        FakeJdbc jdbc = new FakeJdbc(true);
        JdbcIdSegmentDistributor distributor = new JdbcIdSegmentDistributor("ns", "order", 10, dialect.getIncrementMaxIdSql(),
            dialect.getFetchMaxIdSql(), jdbc, Duration.ofSeconds(3));
        assertThat(distributor.getFetchPlan(), is(JdbcIdSegmentDistributor.FetchPlan.RETURNING));
        
        assertThat(distributor.nextMaxId(10), equalTo(42L));
        
        assertThat(jdbc.executed, contains(dialect.getIncrementAndFetchMaxIdSql()));
        assertThat(jdbc.events, contains("close:autoCommit=true"));
    }
    
    @Test
    void h2FetchesInOneQuery() {
        RecordingDataSource dataSource = new RecordingDataSource(RealH2.createDataSource());
        IdSegmentDistributor distributor = DialectTables.segmentDistributorFactory(dataSource, JdbcDialect.H2)
            .create(new IdSegmentDistributorDefinition("ns", "one-query", 5, 10));
        dataSource.getExecutedSql().clear();
        
        assertThat(distributor.nextMaxId(), equalTo(15L));
        assertThat(distributor.nextMaxId(), equalTo(25L));
        
        assertThat(dataSource.getExecutedSql(), contains(JdbcDialect.H2.getIncrementAndFetchMaxIdSql(), JdbcDialect.H2.getIncrementAndFetchMaxIdSql()));
    }
}
