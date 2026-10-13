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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.containsString;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.jdbc.exception.SegmentNameMissingException;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.OptionalLong;

/**
 * Cases of {@link JdbcIdSegmentDistributor} beyond the shared spec, on a real MySQL.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcIdSegmentDistributorMySqlTest {
    DataSource realDataSource;
    RecordingDataSource dataSource;
    String namespace;
    
    @BeforeAll
    void setupDataSource() {
        realDataSource = RealMySql.createDataSource();
    }
    
    @BeforeEach
    void setup() {
        dataSource = new RecordingDataSource(realDataSource);
        namespace = MockIdGenerator.INSTANCE.generateAsString();
    }
    
    private JdbcIdSegmentDistributorFactory factory() {
        return new JdbcIdSegmentDistributorFactory(dataSource, true, new JdbcIdSegmentInitializer(dataSource),
            JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL, JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL);
    }
    
    private OptionalLong segmentMaxId(String name) throws SQLException {
        try (Connection connection = realDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("select last_max_id from cosid where name=?")) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? OptionalLong.of(resultSet.getLong(1)) : OptionalLong.empty();
            }
        }
    }
    
    @Test
    void nextMaxIdWhenSegmentNameIsMissing() {
        JdbcIdSegmentDistributor distributor = new JdbcIdSegmentDistributor(namespace, "rollback", 100, dataSource);
        
        SegmentNameMissingException actual = Assertions.assertThrows(SegmentNameMissingException.class, () -> distributor.nextMaxId(1));
        
        assertThat(actual.getMessage(), containsString(distributor.getNamespacedName()));
        // The built-in MySQL statements run under auto-commit: no transaction to roll back.
        assertThat(dataSource.getRollbackCount(), equalTo(0));
    }
    
    @Test
    void theBuiltInStatementsUseLastInsertId() {
        IdSegmentDistributor distributor = factory().create(new IdSegmentDistributorDefinition(namespace, "last-insert-id", 1, 100));
        dataSource.getExecutedSql().clear();
        
        assertThat(distributor.nextMaxId(), equalTo(101L));
        assertThat(distributor.nextMaxId(), equalTo(201L));
        
        assertThat(dataSource.getExecutedSql(), contains(JdbcIdSegmentDistributor.LAST_INSERT_ID_INCREMENT_MAX_ID_SQL,
            JdbcIdSegmentDistributor.LAST_INSERT_ID_INCREMENT_MAX_ID_SQL));
    }
    
    @Test
    void nextMaxIdShouldCommitWithoutRollbackAndUseQueryTimeout() {
        IdSegmentDistributor distributor = factory().create(new IdSegmentDistributorDefinition(namespace, "commit", 1, 100));
        dataSource.getQueryTimeouts().clear();
        
        assertThat(distributor.nextMaxId(), equalTo(101L));
        
        assertThat(dataSource.getRollbackCount(), equalTo(0));
        assertThat(dataSource.getQueryTimeouts(), contains(5));
    }
    
    @Test
    void nextMaxIdWhenWrongSql() {
        JdbcIdSegmentDistributor distributor = new JdbcIdSegmentDistributor(namespace, "WrongSql", 100, "WrongSql", "WrongSql", dataSource);
        new JdbcIdSegmentInitializer(dataSource).tryInitIdSegment(distributor.getNamespacedName(), 0);
        
        CosIdException actual = Assertions.assertThrows(CosIdException.class, () -> distributor.nextMaxId(1));
        
        assertThat(actual.getCause(), instanceOf(SQLException.class));
        assertThat(dataSource.getRollbackCount(), equalTo(1));
    }
    
    @Test
    void factoryCreateShouldInitializeSegmentWithDefinitionOffset() throws SQLException {
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "factoryCreateShouldInitialize", 9, 3);
        
        IdSegmentDistributor distributor = factory().create(definition);
        
        assertThat(segmentMaxId(definition.getNamespacedName()), equalTo(OptionalLong.of(9)));
        assertThat(distributor.nextMaxId(), equalTo(12L));
    }
    
    @Test
    void anEarlierInsertOnTheSameConnectionDoesNotLeakIntoTheMaxId() throws SQLException {
        try (Connection connection = realDataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                "create table if not exists cosid_other_auto_increment (id bigint not null auto_increment primary key, v int)")) {
                statement.execute();
            }
            DataSource sameConnection = SingleConnectionDataSource.of(connection);
            IdSegmentDistributor distributor = new JdbcIdSegmentDistributorFactory(sameConnection, true, new JdbcIdSegmentInitializer(sameConnection),
                JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL, JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL)
                .create(new IdSegmentDistributorDefinition(namespace, "other-insert", 0, 10));
            for (int i = 1; i <= 3; i++) {
                try (PreparedStatement insert = connection.prepareStatement("insert into cosid_other_auto_increment (v) values (0), (0), (0)")) {
                    insert.executeUpdate();
                }
                assertThat(distributor.nextMaxId(), equalTo(10L * i));
            }
        }
    }
}
