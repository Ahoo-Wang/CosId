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

import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;

/**
 * Runs {@link GroupedIdSegmentDistributorSpec} against a real MySQL.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupedJdbcIdSegmentDistributorContractTest extends GroupedIdSegmentDistributorSpec {
    DataSource dataSource;
    JdbcIdSegmentDistributorFactory distributorFactory;
    
    @BeforeAll
    void setup() {
        dataSource = RealMySql.createDataSource();
        distributorFactory = new JdbcIdSegmentDistributorFactory(dataSource, true, new JdbcIdSegmentInitializer(dataSource),
            JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL, JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL);
    }
    
    @Override
    protected IdSegmentDistributorFactory getFactory() {
        return distributorFactory;
    }
    
    /**
     * The grouped counter ({@code name@group}) going backwards is detected like an ungrouped one.
     */
    @Test
    @Override
    @SneakyThrows
    public void nextMaxIdWhenBack() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        String name = "nextMaxIdWhenBack";
        IdSegmentDistributor distributor = factory().create(new IdSegmentDistributorDefinition(namespace, name, TEST_OFFSET, TEST_STEP));
        GroupedKey groupedKey = groupedSupplier().get();
        
        assertThat(distributor.nextMaxId(), equalTo(TEST_OFFSET + TEST_STEP));
        RealMySql.setSegmentMaxId(dataSource, IdSegmentDistributor.getNamespacedName(namespace, name + "@" + groupedKey.getKey()), TEST_OFFSET);
        
        Assertions.assertThrows(IllegalStateException.class, distributor::nextMaxId);
    }
}
