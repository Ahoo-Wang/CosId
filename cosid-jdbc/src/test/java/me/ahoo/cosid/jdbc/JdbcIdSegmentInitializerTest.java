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

import me.ahoo.cosid.test.Assert;
import me.ahoo.cosid.test.MockIdGenerator;

import lombok.SneakyThrows;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLIntegrityConstraintViolationException;

/**
 * JdbcIdSegmentInitializerTest .
 *
 * @author ahoo wang
 */
class JdbcIdSegmentInitializerTest {
    DataSource dataSource;
    private JdbcIdSegmentInitializer idSegmentInitializer;
    
    @BeforeEach
    private void setup() {
        dataSource = DataSourceFactory.INSTANCE.createDataSource();
        idSegmentInitializer = new JdbcIdSegmentInitializer(dataSource);
    }
    
    
    @SneakyThrows
    @Test
    void initCosIdTable() {
        idSegmentInitializer.initCosIdTable();
    }
    
    @Test
    void tryInitCosIdTable() {
        idSegmentInitializer.tryInitCosIdTable();
    }
    
    @SneakyThrows
    @Test
    void initIdSegment() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        int actual = idSegmentInitializer.initIdSegment(namespace, 0);
        assertThat(actual, Matchers.equalTo(1));
        Assert.assertThrows(SQLIntegrityConstraintViolationException.class, () -> idSegmentInitializer.initIdSegment(namespace, 0));
    }
    
    @Test
    void tryInitIdSegment() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        boolean actual = idSegmentInitializer.tryInitIdSegment(namespace, 0);
        assertThat(actual, Matchers.equalTo(true));
        
        actual = idSegmentInitializer.tryInitIdSegment(namespace, 0);
        assertThat(actual, Matchers.equalTo(false));
    }
}
