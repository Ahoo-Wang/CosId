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
import static org.hamcrest.Matchers.equalTo;

import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.segment.grouped.GroupedKey;
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.test.segment.distributor.GroupedIdSegmentDistributorSpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author ahoo wang
 */
class GroupedJdbcIdSegmentDistributorTest extends GroupedIdSegmentDistributorSpec {
    InMemoryJdbcDataSource dataSource;
    JdbcIdSegmentDistributorFactory distributorFactory;
    JdbcIdSegmentInitializer jdbcIdSegmentInitializer;
    
    @BeforeEach
    void setup() {
        dataSource = DataSourceFactory.INSTANCE.createDataSource();
        jdbcIdSegmentInitializer = new JdbcIdSegmentInitializer(dataSource);
        distributorFactory =
            new JdbcIdSegmentDistributorFactory(dataSource, true, jdbcIdSegmentInitializer, JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL, JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL);
    }
    
    @Override
    protected IdSegmentDistributorFactory getFactory() {
        return distributorFactory;
    }

    @Test
    @Override
    public void nextMaxIdWhenBack() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        String name = "nextMaxIdWhenBack";
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, name, TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = factory().create(definition);
        GroupedKey groupedKey = groupedSupplier().get();
        String groupedNamespacedName = IdSegmentDistributor.getNamespacedName(namespace, name + "@" + groupedKey.getKey());

        long firstMaxId = distributor.nextMaxId();
        assertThat(firstMaxId, equalTo(TEST_OFFSET + TEST_STEP));

        dataSource.setSegmentMaxId(groupedNamespacedName, TEST_OFFSET);
        long nextMaxId = distributor.nextMaxId();

        assertThat(nextMaxId, equalTo(TEST_OFFSET + TEST_STEP));
        assertThat(dataSource.getSegmentMaxId(groupedNamespacedName), equalTo(TEST_OFFSET + TEST_STEP));
    }
}
