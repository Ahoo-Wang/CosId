/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

import com.zaxxer.hikari.HikariDataSource;
import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.SegmentChainId;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author ahoo wang
 */
public class DataSourceFactory {

    public static final DataSourceFactory INSTANCE = new DataSourceFactory();

    AtomicInteger counter = new AtomicInteger();
    private DataSource dataSource;

    private DataSourceFactory() {

    }

    public synchronized DataSource createDataSource() {
        if (dataSource != null) {
            return dataSource;
        }
        HikariDataSource hikariDataSource = new HikariDataSource();
        hikariDataSource.setJdbcUrl("jdbc:mysql://localhost:3306/test_db");
        hikariDataSource.setUsername("root");
        hikariDataSource.setPassword("root");
        dataSource = hikariDataSource;
        return dataSource;
    }

    public JdbcIdSegmentDistributor createJdbcDistributor(int step) {
        DataSource dataSource = createDataSource();
        return createJdbcDistributor(dataSource, step);
    }

    public JdbcIdSegmentDistributor createJdbcDistributor(DataSource dataSource, int step) {
        String namespace = "jbh-" + counter.incrementAndGet();

        JdbcIdSegmentInitializer jdbcIdSegmentInitializer = new JdbcIdSegmentInitializer(dataSource);
        JdbcIdSegmentDistributor jdbcIdSegmentDistributor = new JdbcIdSegmentDistributor(namespace, String.valueOf(step), step, dataSource);
        jdbcIdSegmentInitializer.tryInitIdSegment(jdbcIdSegmentDistributor.getNamespacedName(), 0);
        return jdbcIdSegmentDistributor;
    }

    public DefaultSegmentId createSegmentId(int step) {
        JdbcIdSegmentDistributor distributor = createJdbcDistributor(step);
        return new DefaultSegmentId(distributor);
    }


    public SegmentChainId createSegmentChainId(int step) {
        JdbcIdSegmentDistributor distributor = createJdbcDistributor(step);
        return new SegmentChainId(distributor);
    }

}
