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

import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;

import jakarta.annotation.Nonnull;

import javax.sql.DataSource;

/**
 * Jdbc IdSegment Distributor Factory.
 *
 * @author ahoo wang
 */
public class JdbcIdSegmentDistributorFactory implements IdSegmentDistributorFactory {
    private final DataSource dataSource;
    private final boolean enableAutoInitIdSegment;
    private final JdbcIdSegmentInitializer jdbcIdSegmentInitializer;
    private final String incrementMaxIdSql;
    private final String fetchMaxIdSql;

    public JdbcIdSegmentDistributorFactory(DataSource dataSource, boolean enableAutoInitIdSegment, JdbcIdSegmentInitializer jdbcIdSegmentInitializer, String incrementMaxIdSql, String fetchMaxIdSql) {
        this.dataSource = dataSource;
        this.enableAutoInitIdSegment = enableAutoInitIdSegment;
        this.jdbcIdSegmentInitializer = jdbcIdSegmentInitializer;
        this.incrementMaxIdSql = incrementMaxIdSql;
        this.fetchMaxIdSql = fetchMaxIdSql;
    }

    @Nonnull
    @Override
    public IdSegmentDistributor create(IdSegmentDistributorDefinition definition) {
        if (enableAutoInitIdSegment) {
            jdbcIdSegmentInitializer.tryInitIdSegment(definition.getNamespacedName(), definition.getOffset());
        }
        return new JdbcIdSegmentDistributor(
            definition.getNamespace(), definition.getName(), definition.getStep(),
            incrementMaxIdSql, fetchMaxIdSql, dataSource
        );
    }
}
