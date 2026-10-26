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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import me.ahoo.cosid.CosIdException;
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
import me.ahoo.cosid.spring.boot.starter.machine.ConditionalOnCosIdMachineEnabled;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdJdbcMachineIdDistributorAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;
import me.ahoo.cosid.spring.boot.starter.segment.ConditionalOnCosIdSegmentEnabled;
import me.ahoo.cosid.spring.boot.starter.segment.CosIdJdbcSegmentAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties;
import me.ahoo.cosid.test.container.PostgresLauncher;

import lombok.SneakyThrows;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;

/**
 * The SQL properties left unset on databases other than MySQL: the tables are created, segments fetched and machine ids
 * leased with the database's own statements, chosen from the connection when it is first needed.
 */
class JdbcDialectStarterTest {
    
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
        worksWithoutSqlProperties(h2(""));
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
    void h2InMySqlAndMariaDbModeWorksWithoutSqlProperties() {
        worksWithoutSqlProperties(h2(";MODE=MySQL"));
        worksWithoutSqlProperties(h2(";MODE=MariaDB"));
    }
    
    /**
     * Hands out no connection until {@link #up} is set: a database down at startup.
     */
    @SneakyThrows
    private static DataSource downUntil(AtomicBoolean up, DataSource delegate) {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenAnswer(invocation -> {
            if (!up.get()) {
                throw new SQLException("down", "08001");
            }
            return delegate.getConnection();
        });
        return dataSource;
    }
    
    @Test
    void aDatabaseDownAtStartupIsUsedWithItsOwnStatementsOnceUp() throws SQLException {
        AtomicBoolean up = new AtomicBoolean(false);
        DataSource h2 = h2("");
        new JdbcIdSegmentInitializer(JdbcDialect.H2.getInitCosIdTableSql(), JdbcDialect.H2.getInitIdSegmentSql(), h2).initCosIdTable();
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CosIdJdbcSegmentAutoConfiguration.class))
            .withBean(DataSource.class, () -> downUntil(up, h2))
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true", SegmentIdProperties.Distributor.TYPE + "=jdbc",
                SegmentIdProperties.PREFIX + ".distributor.jdbc.enable-auto-init-cosid-table=true")
            .run(context -> {
                assertThat(context).hasNotFailed();
                IdSegmentDistributor distributor = context.getBean(IdSegmentDistributorFactory.class)
                    .create(new IdSegmentDistributorDefinition("down-" + UUID.randomUUID(), "order", 0, 10));
                assertThatThrownBy(distributor::nextMaxId).isInstanceOf(CosIdException.class);
                
                up.set(true);
                
                // The row the factory could not insert while the database was down.
                new JdbcIdSegmentInitializer(h2).tryInitIdSegment(distributor.getNamespacedName(), 0);
                assertThat(distributor.nextMaxId()).isEqualTo(10L);
            });
    }
    
    @Test
    void aHungDatabaseDoesNotBlockTheStartupOfSegments() throws SQLException {
        CountDownLatch released = new CountDownLatch(1);
        DataSource hung = mock(DataSource.class);
        when(hung.getConnection()).thenAnswer(invocation -> {
            released.await();
            throw new SQLException("hung", "08001");
        });
        try {
            long started = System.nanoTime();
            new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CosIdJdbcSegmentAutoConfiguration.class))
                .withBean(DataSource.class, () -> hung)
                .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true", SegmentIdProperties.Distributor.TYPE + "=jdbc")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(IdSegmentDistributorFactory.class));
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(5_000L);
        } finally {
            released.countDown();
        }
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
    
    private static DataSource h2(String mode) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:starter_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1" + mode);
        return dataSource;
    }
}
