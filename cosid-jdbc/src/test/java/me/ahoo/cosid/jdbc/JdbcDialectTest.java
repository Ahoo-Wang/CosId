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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.Optional;
import javax.sql.DataSource;

class JdbcDialectTest {
    private static final java.util.Map<JdbcDialect, String> PRODUCTS = java.util.Map.of(JdbcDialect.MYSQL, "MySQL", JdbcDialect.POSTGRESQL, "PostgreSQL",
        JdbcDialect.H2, "H2", JdbcDialect.SQL_SERVER, "Microsoft SQL Server");
    
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
    
    @ParameterizedTest
    @CsvSource({";MODE=MySQL", ";MODE=MariaDB", ";MODE=PostgreSQL", "''"})
    void h2IsSupportedOutsideOracleMode(String mode) throws Exception {
        assertThat(JdbcDialect.of(h2(mode)), equalTo(Optional.of(JdbcDialect.H2)));
    }
    
    @Test
    void h2InOracleModeIsUnsupported() throws Exception {
        // Oracle mode stores '' as NULL: a released lease (instance_id='') can not be written nor found.
        assertThat(JdbcDialect.of(h2(";MODE=Oracle")), equalTo(Optional.empty()));
        JdbcDataSource h2 = h2("");
        try (java.sql.Connection connection = h2.getConnection(); java.sql.Statement statement = connection.createStatement()) {
            statement.execute("set mode oracle");
            assertThat(JdbcDialect.ofConnection(connection), equalTo(Optional.empty()));
        }
    }
    
    @Test
    void anUnreadableH2ModeKeepsH2() throws Exception {
        // FakeJdbc can not run the mode query.
        try (java.sql.Connection connection = new FakeJdbc(true).getConnection()) {
            assertThat(JdbcDialect.ofProduct(connection, "H2"), equalTo(Optional.of(JdbcDialect.H2)));
        }
    }
    
    @Test
    void h2InOracleModeTakesNoStatementsOfItsOwn() throws Exception {
        JdbcDataSource h2 = h2(";MODE=Oracle");
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> new JdbcMachineLeaseStore(h2, Duration.ofSeconds(3)));
        assertThat(exception.getMessage(), containsString("Oracle compatibility mode"));
        
        RecordingDataSource recording = new RecordingDataSource(h2);
        // The MySQL DDL runs as configured, as on any unsupported database.
        assertThrows(java.sql.SQLException.class, () -> new JdbcMachineIdInitializer(recording).initCosIdMachineTable());
        assertThat(recording.getExecutedSql(), hasItem(JdbcMachineIdInitializer.INIT_COSID_MACHINE_TABLE_SQL));
    }
    
    @Test
    void theIndexStatementsFollowTheDatabaseWithTheDefaultTableOnly() throws Exception {
        RecordingDataSource defaults = new RecordingDataSource(h2(""));
        new JdbcMachineIdInitializer(defaults).initCosIdMachineTable();
        assertThat(defaults.getExecutedSql(), hasItem(JdbcDialect.H2.getInitNamespaceIdxSql()));
        assertThat(defaults.getExecutedSql(), hasItem(JdbcDialect.H2.getInitInstanceIdIdxSql()));
        
        // A table DDL of the user's with the index statements left empty: no index of ours next to theirs.
        RecordingDataSource own = new RecordingDataSource(h2(""));
        new JdbcMachineIdInitializer(own, JdbcDialect.H2.getInitCosIdMachineTableSql(), JdbcMachineIdInitializer.INIT_NAMESPACE_IDX_SQL,
            JdbcMachineIdInitializer.INIT_INSTANCE_ID_IDX_SQL).initCosIdMachineTable();
        assertThat(own.getExecutedSql(), hasItem(JdbcDialect.H2.getInitCosIdMachineTableSql()));
        assertThat(own.getExecutedSql(), not(hasItem(containsString("create index"))));
    }
    
    private static JdbcDataSource h2(String mode) {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:dialect_" + java.util.UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1" + mode);
        return h2;
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
        jdbc.product = PRODUCTS.get(dialect);
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
        
        // The H2 mode is read once, with the product.
        assertThat(dataSource.getExecutedSql(), contains(JdbcDialect.H2_MODE_SQL, JdbcDialect.H2.getIncrementAndFetchMaxIdSql(),
            JdbcDialect.H2.getIncrementAndFetchMaxIdSql()));
    }
    
    @ParameterizedTest
    @EnumSource(value = JdbcDialect.class, names = {"POSTGRESQL", "H2", "SQL_SERVER"})
    void theMySqlDefaultsOnAnotherDatabaseTakeItsOwnStatements(JdbcDialect dialect) {
        FakeJdbc jdbc = new FakeJdbc(true);
        jdbc.product = PRODUCTS.get(dialect);
        JdbcIdSegmentDistributor distributor = new JdbcIdSegmentDistributor("ns", "order", 10, JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL,
            JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL, jdbc, Duration.ofSeconds(3));
        
        distributor.nextMaxId(10);
        jdbc.maxId = 52;
        distributor.nextMaxId(10);
        
        assertThat(jdbc.executed, contains(dialect.getIncrementAndFetchMaxIdSql(), dialect.getIncrementAndFetchMaxIdSql()));
        // Read once, then kept.
        assertThat(jdbc.metadataReads, equalTo(1));
    }
    
    @Test
    void theMySqlFastPathOnlyOnMySql() {
        FakeJdbc jdbc = new FakeJdbc(true);
        jdbc.product = "Oracle";
        JdbcIdSegmentDistributor distributor = new JdbcIdSegmentDistributor("ns", "order", 10, JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL,
            JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL + ";", jdbc, Duration.ofSeconds(3));
        
        distributor.nextMaxId(10);
        
        assertThat(jdbc.executed, contains(JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL, JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL + ";"));
        assertThat(jdbc.events, contains("autoCommit=false", "commit", "autoCommit=true", "close:autoCommit=true"));
    }
    
    @Test
    void anUnreadableProductFetchesInATransactionAndIsReadAgain() {
        FakeJdbc jdbc = new FakeJdbc(true);
        jdbc.product = null;
        JdbcIdSegmentDistributor distributor = new JdbcIdSegmentDistributor("ns", "order", 10, JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL,
            JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL, jdbc, Duration.ofSeconds(3));
        
        distributor.nextMaxId(10);
        jdbc.product = "MySQL";
        jdbc.maxId = 52;
        distributor.nextMaxId(10);
        
        assertThat(jdbc.executed, contains(JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL, JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL,
            JdbcIdSegmentDistributor.LAST_INSERT_ID_INCREMENT_MAX_ID_SQL));
        assertThat(jdbc.metadataReads, equalTo(2));
    }
    
    @ParameterizedTest
    @CsvSource({";MODE=MySQL", ";MODE=MariaDB", "''"})
    void h2InItsSupportedModesWithTheMySqlDefaults(String mode) throws Exception {
        DataSource h2 = RealH2.createDataSource(mode);
        IdSegmentDistributor distributor = new JdbcIdSegmentDistributorFactory(h2, true, new JdbcIdSegmentInitializer(h2),
            JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL, JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL)
            .create(new IdSegmentDistributorDefinition("ns", "mysql-defaults", 3, 10));
        
        assertThat(distributor.nextMaxId(), equalTo(13L));
        assertThat(distributor.nextMaxId(), equalTo(23L));
    }
    
    @ParameterizedTest
    @CsvSource({";MODE=MySQL", ";MODE=MariaDB", "''"})
    void theInitializersAndTheLeaseStoreFollowTheDatabaseWithTheMySqlDefaults(String mode) throws Exception {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:defaults_" + java.util.UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1" + mode);
        new JdbcIdSegmentInitializer(h2).initCosIdTable();
        new JdbcMachineIdInitializer(h2).initCosIdMachineTable();
        
        JdbcMachineLeaseStore store = new JdbcMachineLeaseStore(h2);
        
        assertThat(store.getServerTimeSql(), equalTo(JdbcDialect.H2.getServerTimeSql()));
        assertThat(Math.abs(store.serverTime() - System.currentTimeMillis()) < 60_000, is(true));
        assertThat(new JdbcMachineLeaseStore(h2, Duration.ofSeconds(3)).getServerTimeSql(), equalTo(JdbcDialect.H2.getServerTimeSql()));
    }
}
