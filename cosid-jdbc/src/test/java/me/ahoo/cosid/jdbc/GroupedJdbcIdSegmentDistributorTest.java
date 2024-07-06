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

import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.test.segment.distributor.GroupedIdSegmentDistributorSpec;

import org.junit.jupiter.api.BeforeEach;

import javax.sql.DataSource;

/**
 * @author ahoo wang
 */
class GroupedJdbcIdSegmentDistributorTest extends GroupedIdSegmentDistributorSpec {
    DataSource dataSource;
    JdbcIdSegmentDistributorFactory distributorFactory;
    JdbcIdSegmentInitializer mySqlIdSegmentInitializer;
    
    @BeforeEach
    void setup() {
        dataSource = DataSourceFactory.INSTANCE.createDataSource();
        mySqlIdSegmentInitializer = new JdbcIdSegmentInitializer(dataSource);
        distributorFactory =
            new JdbcIdSegmentDistributorFactory(dataSource, true, mySqlIdSegmentInitializer, JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL, JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL);
    }
    
    @Override
    protected IdSegmentDistributorFactory getFactory() {
        return distributorFactory;
    }
}
