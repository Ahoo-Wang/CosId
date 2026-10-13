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

package me.ahoo.cosid.spring.boot.starter.segment;

import me.ahoo.cosid.jdbc.JdbcDialect;
import me.ahoo.cosid.jdbc.JdbcIdSegmentDistributor;
import me.ahoo.cosid.jdbc.JdbcIdSegmentDistributorFactory;
import me.ahoo.cosid.jdbc.JdbcIdSegmentInitializer;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.jdbc.JdbcDialectDetector;
import me.ahoo.cosid.spring.boot.starter.store.BuiltInStoreProvider;
import me.ahoo.cosid.spring.boot.starter.store.ConditionalOnBuiltInSegmentStore;
import me.ahoo.cosid.spring.boot.starter.store.ConditionalOnSegmentStoreType;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * CosId Jdbc Segment AutoConfiguration.
 *
 * @author ahoo wang
 */
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdSegmentEnabled
@EnableConfigurationProperties(SegmentIdProperties.class)
@ConditionalOnClass({JdbcIdSegmentInitializer.class, JdbcIdSegmentDistributorFactory.class})
@ConditionalOnSegmentStoreType("jdbc")
public class CosIdJdbcSegmentAutoConfiguration {

    private final SegmentIdProperties segmentIdProperties;
    private @Nullable JdbcDialectDetector detector;

    public CosIdJdbcSegmentAutoConfiguration(SegmentIdProperties segmentIdProperties) {
        this.segmentIdProperties = segmentIdProperties;
    }

    /**
     * The segment table initializer. Statements left at their (MySQL) defaults follow the database product: see
     * {@link JdbcDialectDetector}.
     */
    @Bean
    @ConditionalOnMissingBean
    public JdbcIdSegmentInitializer jdbcIdSegmentInitializer(DataSource dataSource) {
        SegmentIdProperties.Distributor.Jdbc jdbc = segmentIdProperties.getDistributor().getJdbc();
        JdbcDialectDetector detector = detector(dataSource);
        JdbcIdSegmentInitializer segmentInitializer = new JdbcIdSegmentInitializer(
            detector.sql(jdbc.getInitCosidTableSql(), JdbcIdSegmentInitializer.INIT_COSID_TABLE_SQL, JdbcDialect::getInitCosIdTableSql),
            detector.sql(jdbc.getInitIdSegmentSql(), JdbcIdSegmentInitializer.INIT_ID_SEGMENT_SQL, JdbcDialect::getInitIdSegmentSql),
            dataSource, jdbc.getQueryTimeout());
        if (jdbc.isEnableAutoInitCosidTable()) {
            segmentInitializer.tryInitCosIdTable();
        }
        return segmentInitializer;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBuiltInSegmentStore
    public IdSegmentDistributorFactory idSegmentDistributorFactory(DataSource dataSource, JdbcIdSegmentInitializer jdbcIdSegmentInitializer) {
        SegmentIdProperties.Distributor.Jdbc jdbc = segmentIdProperties.getDistributor().getJdbc();
        JdbcDialectDetector detector = detector(dataSource);
        return new JdbcIdSegmentDistributorFactory(dataSource, jdbc.isEnableAutoInitIdSegment(), jdbcIdSegmentInitializer,
            detector.sql(jdbc.getIncrementMaxIdSql(), JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL, JdbcDialect::getIncrementMaxIdSql),
            detector.sql(jdbc.getFetchMaxIdSql(), JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL, JdbcDialect::getFetchMaxIdSql),
            jdbc.getQueryTimeout(), JdbcIdSegmentDistributor.FetchMode.valueOf(jdbc.getFetchPlan().name()));
    }
    
    /**
     * One detection for the initializer and the factory of the same {@link DataSource}.
     */
    private synchronized JdbcDialectDetector detector(DataSource dataSource) {
        if (detector == null || detector.getDataSource() != dataSource) {
            detector = new JdbcDialectDetector(dataSource);
        }
        return detector;
    }

    /**
     * The JDBC backend for {@link me.ahoo.cosid.spring.boot.starter.store.CosIdStoreProvider} selection.
     */
    @Bean
    public BuiltInStoreProvider cosIdJdbcSegmentStoreProvider(ObjectProvider<DataSource> dataSource, ObjectProvider<JdbcIdSegmentInitializer> jdbcIdSegmentInitializer) {
        return BuiltInStoreProvider.segment("jdbc", context -> idSegmentDistributorFactory(dataSource.getObject(), jdbcIdSegmentInitializer.getObject()));
    }

}
