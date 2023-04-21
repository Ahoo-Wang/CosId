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

import me.ahoo.cosid.jdbc.JdbcIdSegmentDistributorFactory;
import me.ahoo.cosid.jdbc.JdbcIdSegmentInitializer;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(value = SegmentIdProperties.Distributor.TYPE, matchIfMissing = true, havingValue = "jdbc")
public class CosIdJdbcSegmentAutoConfiguration {

    private final SegmentIdProperties segmentIdProperties;

    public CosIdJdbcSegmentAutoConfiguration(SegmentIdProperties segmentIdProperties) {
        this.segmentIdProperties = segmentIdProperties;
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcIdSegmentInitializer jdbcIdSegmentInitializer(DataSource dataSource) {
        SegmentIdProperties.Distributor.Jdbc jdbc = segmentIdProperties.getDistributor().getJdbc();
        JdbcIdSegmentInitializer segmentInitializer = new JdbcIdSegmentInitializer(jdbc.getInitCosidTableSql(), jdbc.getInitIdSegmentSql(), dataSource);
        if (jdbc.isEnableAutoInitCosidTable()) {
            segmentInitializer.tryInitCosIdTable();
        }
        return segmentInitializer;
    }

    @Bean
    @ConditionalOnMissingBean
    public IdSegmentDistributorFactory idSegmentDistributorFactory(DataSource dataSource, JdbcIdSegmentInitializer jdbcIdSegmentInitializer) {
        SegmentIdProperties.Distributor.Jdbc jdbc = segmentIdProperties.getDistributor().getJdbc();
        return new JdbcIdSegmentDistributorFactory(dataSource, jdbc.isEnableAutoInitIdSegment(), jdbcIdSegmentInitializer, jdbc.getIncrementMaxIdSql(), jdbc.getFetchMaxIdSql());
    }

}
