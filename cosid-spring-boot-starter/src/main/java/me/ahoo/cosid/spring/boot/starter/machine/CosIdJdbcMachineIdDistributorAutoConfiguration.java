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

package me.ahoo.cosid.spring.boot.starter.machine;

import me.ahoo.cosid.jdbc.JdbcDialect;
import me.ahoo.cosid.jdbc.JdbcMachineIdInitializer;
import me.ahoo.cosid.jdbc.JdbcMachineLeaseStore;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.jdbc.JdbcDialectDetector;
import me.ahoo.cosid.spring.boot.starter.snowflake.ConditionalOnCosIdSnowflakeEnabled;
import me.ahoo.cosid.spring.boot.starter.snowflake.SnowflakeIdProperties;
import me.ahoo.cosid.spring.boot.starter.store.BuiltInStoreProvider;
import me.ahoo.cosid.spring.boot.starter.store.ConditionalOnBuiltInMachineStore;
import me.ahoo.cosid.spring.boot.starter.store.ConditionalOnMachineStoreType;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * CosId Jdbc MachineIdDistributor AutoConfiguration.
 *
 * @author ahoo wang
 */
@AutoConfiguration
@AutoConfigureBefore(CosIdMachineAutoConfiguration.class)
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdMachineEnabled
@ConditionalOnClass(JdbcMachineLeaseStore.class)
@ConditionalOnMachineStoreType("jdbc")
@EnableConfigurationProperties(MachineProperties.class)
public class CosIdJdbcMachineIdDistributorAutoConfiguration {

    private final MachineProperties machineProperties;
    private @Nullable JdbcDialectDetector detector;

    public CosIdJdbcMachineIdDistributorAutoConfiguration(MachineProperties machineProperties) {
        this.machineProperties = machineProperties;
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcMachineIdInitializer jdbcMachineIdInitializer(DataSource dataSource) {
        MachineProperties.Jdbc jdbc = machineProperties.getDistributor().getJdbc();
        JdbcMachineIdInitializer machineIdInitializer;
        if (jdbc.isEnableAutoInitCosidMachineTable()) {
            // Statements left at their (MySQL) defaults follow the database product.
            JdbcDialectDetector detector = detector(dataSource);
            machineIdInitializer = new JdbcMachineIdInitializer(
                dataSource,
                detector.sql(jdbc.getInitCosIdMachineTableSql(), JdbcMachineIdInitializer.INIT_COSID_MACHINE_TABLE_SQL, JdbcDialect::getInitCosIdMachineTableSql),
                detector.sql(jdbc.getInitNamespaceIdxSql(), JdbcMachineIdInitializer.INIT_NAMESPACE_IDX_SQL, JdbcDialect::getInitNamespaceIdxSql),
                detector.sql(jdbc.getInitInstanceIdIdxSql(), JdbcMachineIdInitializer.INIT_INSTANCE_ID_IDX_SQL, JdbcDialect::getInitInstanceIdIdxSql),
                jdbc.getQueryTimeout());
        } else {
            machineIdInitializer = new JdbcMachineIdInitializer(dataSource, jdbc.getInitCosIdMachineTableSql(), jdbc.getInitNamespaceIdxSql(),
                jdbc.getInitInstanceIdIdxSql(), jdbc.getQueryTimeout());
        }
        if (jdbc.isEnableAutoInitCosidMachineTable()) {
            machineIdInitializer.tryInitCosIdMachineTable();
        }
        return machineIdInitializer;
    }

    /**
     * The machine id lease store on the {@code cosid_machine} table. It fails at startup, naming the {@code ALTER
     * TABLE}, when the table lacks the 4.0 lease columns. The server clock query follows
     * {@code cosid.machine.distributor.jdbc.server-time-sql}, or the database product ({@link JdbcDialect}).
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBuiltInMachineStore
    public JdbcMachineLeaseStore jdbcMachineLeaseStore(JdbcMachineIdInitializer jdbcMachineIdInitializer, DataSource dataSource) {
        MachineProperties.Jdbc jdbc = machineProperties.getDistributor().getJdbc();
        String serverTimeSql = jdbc.getServerTimeSql() != null ? jdbc.getServerTimeSql() : serverTimeSqlOf(detector(dataSource));
        return new JdbcMachineLeaseStore(dataSource, jdbc.getQueryTimeout(), serverTimeSql);
    }
    
    private static String serverTimeSqlOf(JdbcDialectDetector detector) {
        JdbcDialect dialect = detector.dialect();
        if (dialect != null) {
            return dialect.getServerTimeSql();
        }
        String product = detector.product();
        if (product == null) {
            throw new IllegalStateException("Can not read the database product to choose the server time query: set "
                + MachineProperties.PREFIX + ".distributor.jdbc.server-time-sql.");
        }
        throw new IllegalStateException("No server time query known for database [" + product + "]: set "
            + MachineProperties.PREFIX + ".distributor.jdbc.server-time-sql to a query returning the server's current time in milliseconds.");
    }
    
    /**
     * One detection for the initializer and the lease store of the same {@link DataSource}.
     */
    private synchronized JdbcDialectDetector detector(DataSource dataSource) {
        if (detector == null || detector.getDataSource() != dataSource) {
            detector = new JdbcDialectDetector(dataSource);
        }
        return detector;
    }
    
    @Bean
    @ConditionalOnMissingBean(MachineIdDistributor.class)
    @ConditionalOnBuiltInMachineStore
    public LeaseStoreMachineIdDistributor jdbcMachineIdDistributor(
        JdbcMachineLeaseStore jdbcMachineLeaseStore,
        MachineStateStorage localMachineState,
        ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        return new LeaseStoreMachineIdDistributor(jdbcMachineLeaseStore, localMachineState, clockBackwardsSynchronizer);
    }

    /**
     * The JDBC backend for {@link me.ahoo.cosid.spring.boot.starter.store.CosIdStoreProvider} selection.
     */
    @Bean
    public BuiltInStoreProvider cosIdJdbcMachineStoreProvider(ObjectProvider<JdbcMachineLeaseStore> jdbcMachineLeaseStore,
                                                              ObjectProvider<JdbcMachineIdInitializer> jdbcMachineIdInitializer,
                                                              ObjectProvider<DataSource> dataSource) {
        return BuiltInStoreProvider.machine("jdbc", context -> jdbcMachineIdDistributor(
            jdbcMachineLeaseStore.getIfAvailable(() -> jdbcMachineLeaseStore(jdbcMachineIdInitializer.getObject(), dataSource.getObject())),
            context.getMachineStateStorage(), context.getClockBackwardsSynchronizer()));
    }

}
