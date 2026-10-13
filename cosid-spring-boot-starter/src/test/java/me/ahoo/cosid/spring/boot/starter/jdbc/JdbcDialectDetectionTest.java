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

package me.ahoo.cosid.spring.boot.starter.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import me.ahoo.cosid.jdbc.JdbcDialect;
import me.ahoo.cosid.jdbc.JdbcIdSegmentDistributor;
import me.ahoo.cosid.jdbc.JdbcIdSegmentInitializer;
import me.ahoo.cosid.jdbc.JdbcMachineLeaseStore;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdJdbcMachineIdDistributorAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.ConditionalOnCosIdMachineEnabled;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;
import me.ahoo.cosid.spring.boot.starter.segment.ConditionalOnCosIdSegmentEnabled;
import me.ahoo.cosid.spring.boot.starter.segment.CosIdJdbcSegmentAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties;
import me.ahoo.cosid.test.container.PostgresLauncher;

import org.assertj.core.data.Offset;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * The SQL properties left unset on databases other than MySQL: the starter creates the tables, fetches segments and
 * leases machine ids with the database's own statements.
 */
class JdbcDialectDetectionTest {
    
    private static ApplicationContextRunner runner(DataSource dataSource) {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CosIdJdbcSegmentAutoConfiguration.class, CosIdJdbcMachineIdDistributorAutoConfiguration.class))
            .withBean(DataSource.class, () -> dataSource)
            .withBean(MachineStateStorage.class, () -> MachineStateStorage.IN_MEMORY)
            .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT)
            .withPropertyValues(
                ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true",
                SegmentIdProperties.Distributor.TYPE + "=jdbc",
                SegmentIdProperties.PREFIX + ".distributor.jdbc.enable-auto-init-cosid-table=true",
                ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true",
                MachineProperties.Distributor.TYPE + "=jdbc",
                MachineProperties.PREFIX + ".distributor.jdbc.enable-auto-init-cosid-machine-table=true");
    }
    
    private static void worksWithoutSqlProperties(DataSource dataSource) {
        runner(dataSource).run(context -> {
            assertThat(context).hasNotFailed();
            IdSegmentDistributor distributor = context.getBean(IdSegmentDistributorFactory.class)
                .create(new IdSegmentDistributorDefinition("dialect-" + UUID.randomUUID(), "order", 7, 10));
            assertThat(distributor.nextMaxId()).isEqualTo(17L);
            assertThat(distributor.nextMaxId()).isEqualTo(27L);
            
            JdbcMachineLeaseStore leaseStore = context.getBean(JdbcMachineLeaseStore.class);
            assertThat(leaseStore.serverTime()).isCloseTo(System.currentTimeMillis(), Offset.offset(60_000L));
            assertThat(leaseStore.claimNew("dialect-" + UUID.randomUUID(), InstanceId.of("dialect", false), 1023, leaseStore.serverTime())).isPresent();
        });
    }
    
    @Test
    void h2WorksWithoutSqlProperties() {
        worksWithoutSqlProperties(h2());
    }
    
    @Test
    void postgresqlWorksWithoutSqlProperties() {
        Assumptions.assumeTrue(PostgresLauncher.isAvailable(), "needs Docker or " + PostgresLauncher.URL_ENV);
        PostgresLauncher.Connection connection = PostgresLauncher.getConnection();
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(connection.jdbcUrl());
        dataSource.setUser(connection.username());
        dataSource.setPassword(connection.password());
        worksWithoutSqlProperties(dataSource);
    }
    
    @Test
    void aConfiguredStatementIsKept() {
        JdbcDialectDetector detector = new JdbcDialectDetector(h2());
        
        assertThat(detector.sql("update my_cosid set last_max_id=(last_max_id + ?) where name = ?", JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL,
            JdbcDialect::getIncrementMaxIdSql)).isEqualTo("update my_cosid set last_max_id=(last_max_id + ?) where name = ?");
        assertThat(detector.sql(JdbcIdSegmentInitializer.INIT_COSID_TABLE_SQL, JdbcIdSegmentInitializer.INIT_COSID_TABLE_SQL,
            JdbcDialect::getInitCosIdTableSql)).isEqualTo(JdbcDialect.H2.getInitCosIdTableSql());
        assertThat(detector.dialect()).isEqualTo(JdbcDialect.H2);
        assertThat(detector.product()).isEqualTo("H2");
    }
    
    @Test
    void anUnreadableOrUnsupportedDatabaseKeepsTheMySqlDefaults() throws SQLException {
        DataSource down = mock(DataSource.class);
        when(down.getConnection()).thenThrow(new SQLException("down", "08001"));
        JdbcDialectDetector unreadable = new JdbcDialectDetector(down);
        assertThat(unreadable.sql(JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL, JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL,
            JdbcDialect::getIncrementMaxIdSql)).isEqualTo(JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL);
        assertThat(unreadable.product()).isNull();
        
        DataSource oracle = mock(DataSource.class, Answers.RETURNS_DEEP_STUBS);
        when(oracle.getConnection().getMetaData().getDatabaseProductName()).thenReturn("Oracle");
        JdbcDialectDetector unsupported = new JdbcDialectDetector(oracle);
        assertThat(unsupported.dialect()).isNull();
        assertThat(unsupported.product()).isEqualTo("Oracle");
        assertThat(unsupported.sql(JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL, JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL,
            JdbcDialect::getFetchMaxIdSql)).isEqualTo(JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL);
    }
    
    @Test
    void theServerTimeOfAnUnsupportedDatabaseMustBeConfigured() throws SQLException {
        DataSource oracle = mock(DataSource.class, Answers.RETURNS_DEEP_STUBS);
        when(oracle.getConnection().getMetaData().getDatabaseProductName()).thenReturn("Oracle");
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CosIdJdbcMachineIdDistributorAutoConfiguration.class))
            .withBean(DataSource.class, () -> oracle)
            .withBean(MachineStateStorage.class, () -> MachineStateStorage.IN_MEMORY)
            .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT)
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true", MachineProperties.Distributor.TYPE + "=jdbc")
            .run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("No server time query known for database [Oracle]"));
    }
    
    private static DataSource h2() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:detector_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        return dataSource;
    }
}
