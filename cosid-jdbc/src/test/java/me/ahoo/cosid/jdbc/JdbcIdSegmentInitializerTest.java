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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import me.ahoo.cosid.test.Assert;
import me.ahoo.cosid.test.MockIdGenerator;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 * JdbcIdSegmentInitializerTest .
 *
 * @author ahoo wang
 */
class JdbcIdSegmentInitializerTest {
    InMemoryJdbcDataSource dataSource;
    private JdbcIdSegmentInitializer idSegmentInitializer;
    
    @BeforeEach
    void setup() {
        dataSource = DataSourceFactory.INSTANCE.createDataSource();
        idSegmentInitializer = new JdbcIdSegmentInitializer(dataSource);
    }
    
    @SneakyThrows
    @Test
    void initCosIdTable() {
        int affected = idSegmentInitializer.initCosIdTable();

        assertThat(affected, equalTo(0));
        assertThat(dataSource.isCosIdTableInitialized(), equalTo(true));
        assertThat(dataSource.getExecutedSql(), contains(JdbcIdSegmentInitializer.INIT_COSID_TABLE_SQL));
    }
    
    @Test
    void tryInitCosIdTable() {
        assertThat(idSegmentInitializer.tryInitCosIdTable(), equalTo(true));

        JdbcIdSegmentInitializer wrongSqlInitializer = new JdbcIdSegmentInitializer("WrongSql", JdbcIdSegmentInitializer.INIT_ID_SEGMENT_SQL, dataSource);
        assertThat(wrongSqlInitializer.tryInitCosIdTable(), equalTo(false));
    }
    
    @SneakyThrows
    @Test
    void initIdSegment() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        int actual = idSegmentInitializer.initIdSegment(namespace, 0);
        assertThat(actual, equalTo(1));
        assertThat(dataSource.getSegmentMaxId(namespace), equalTo(0L));
        Assert.assertThrows(SQLIntegrityConstraintViolationException.class, () -> idSegmentInitializer.initIdSegment(namespace, 0));
    }
    
    @Test
    void tryInitIdSegment() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        boolean actual = idSegmentInitializer.tryInitIdSegment(namespace, 0);
        assertThat(actual, equalTo(true));
        assertThat(dataSource.containsSegment(namespace), equalTo(true));
        
        actual = idSegmentInitializer.tryInitIdSegment(namespace, 0);
        assertThat(actual, equalTo(false));
    }

    @Test
    void tryInitIdSegmentWhenWrongSql() {
        JdbcIdSegmentInitializer wrongSqlInitializer = new JdbcIdSegmentInitializer(JdbcIdSegmentInitializer.INIT_COSID_TABLE_SQL, "WrongSql", dataSource);

        assertThat(wrongSqlInitializer.tryInitIdSegment("wrong-sql", 0), equalTo(false));
        assertThat(dataSource.getExecutedSql().get(0), containsString("WrongSql"));
    }
}
