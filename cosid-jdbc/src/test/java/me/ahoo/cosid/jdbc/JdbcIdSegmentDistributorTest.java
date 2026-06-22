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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.jdbc.exception.SegmentNameMissingException;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.test.segment.distributor.IdSegmentDistributorSpec;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

/**
 * @author ahoo wang
 */
class JdbcIdSegmentDistributorTest extends IdSegmentDistributorSpec {
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
    
    
    @Override
    protected <T extends IdSegmentDistributor> void setMaxIdBack(T distributor, long maxId) {
        dataSource.setSegmentMaxId(distributor.getNamespacedName(), maxId);
    }
    
    @Test
    @Override
    public void nextMaxIdWhenBack() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "nextMaxIdWhenBack", TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = factory().create(definition);
        long firstMaxId = distributor.nextMaxId();
        assertThat(firstMaxId, equalTo(TEST_OFFSET + TEST_STEP));

        setMaxIdBack(distributor, TEST_OFFSET);
        long nextMaxId = distributor.nextMaxId();

        assertThat(nextMaxId, equalTo(TEST_OFFSET + TEST_STEP));
        assertThat(dataSource.getSegmentMaxId(distributor.getNamespacedName()), equalTo(TEST_OFFSET + TEST_STEP));
    }
    
    @Test
    void nextMaxIdWhenSegmentNameMissing() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        JdbcIdSegmentDistributor jdbcIdSegmentDistributor = new JdbcIdSegmentDistributor(namespace, "SegmentNameMissing", 100, dataSource);
        SegmentNameMissingException actual = Assertions.assertThrows(SegmentNameMissingException.class, () -> jdbcIdSegmentDistributor.nextMaxId(1));
        assertThat(actual.getMessage(), containsString(jdbcIdSegmentDistributor.getNamespacedName()));
    }
    
    @Test
    void nextMaxIdWhenWrongSql() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        JdbcIdSegmentDistributor jdbcIdSegmentDistributor = new JdbcIdSegmentDistributor(namespace, "WrongSql", 100, "WrongSql", "WrongSql", dataSource);
        jdbcIdSegmentInitializer.tryInitIdSegment(jdbcIdSegmentDistributor.getNamespacedName(), 0);

        CosIdException actual = Assertions.assertThrows(CosIdException.class, () -> jdbcIdSegmentDistributor.nextMaxId(1));

        assertThat(actual.getCause(), instanceOf(SQLException.class));
        assertThat(actual.getMessage(), containsString("Unsupported SQL"));
    }

    @Test
    void factoryCreateShouldInitializeSegmentWithDefinitionOffset() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "factoryCreateShouldInitialize", 9, 3);
        IdSegmentDistributor distributor = distributorFactory.create(definition);

        assertThat(dataSource.containsSegment(definition.getNamespacedName()), equalTo(true));
        assertThat(dataSource.getSegmentMaxId(definition.getNamespacedName()), equalTo(9L));
        assertThat(distributor.nextMaxId(), equalTo(12L));
    }
}
