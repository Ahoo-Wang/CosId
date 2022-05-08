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

import me.ahoo.cosid.jdbc.exception.SegmentNameMissingException;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.segment.SegmentId;
import me.ahoo.cosid.test.ConcurrentGenerateSpec;
import me.ahoo.cosid.test.MockIdGenerator;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcIdSegmentDistributorTest {
    DataSource dataSource;
    private JdbcIdSegmentInitializer mySqlIdSegmentInitializer;
    private IdSegmentDistributor mySqlIdSegmentDistributor;
    
    @BeforeAll
    private void setup() {
        dataSource = DataSourceFactory.INSTANCE.createDataSource();
        mySqlIdSegmentInitializer = new JdbcIdSegmentInitializer(dataSource);
        JdbcIdSegmentDistributorFactory distributorFactory =
            new JdbcIdSegmentDistributorFactory(dataSource, true, mySqlIdSegmentInitializer, JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL, JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL);
        mySqlIdSegmentDistributor = distributorFactory.create(new IdSegmentDistributorDefinition("JdbcIdSegmentDistributorTest", MockIdGenerator.INSTANCE.generateAsString(), 0, 100));
        mySqlIdSegmentInitializer.tryInitIdSegment(mySqlIdSegmentDistributor.getNamespacedName(), 0);
    }
    
    @Test
    void nextMaxId() {
        long id = mySqlIdSegmentDistributor.nextMaxId(100);
        Assertions.assertEquals(100, id);
    }
    
    @Test
    void initSchema() {
        mySqlIdSegmentInitializer.tryInitCosIdTable();
    }
    
    @Test
    void nextMaxIdWhenMissingIdSegment() {
        JdbcIdSegmentDistributor missingDistributor = new JdbcIdSegmentDistributor("JdbcIdSegmentDistributorTest", MockIdGenerator.INSTANCE.generateAsString(), 10, dataSource);
        Assertions.assertThrows(SegmentNameMissingException.class, () -> missingDistributor.nextMaxId(100));
    }
    
    @SneakyThrows
    @Test
    void missing_init() {
        JdbcIdSegmentDistributor missingDistributor = new JdbcIdSegmentDistributor("JdbcIdSegmentDistributorTest", MockIdGenerator.INSTANCE.generateAsString(), 10, dataSource);
        mySqlIdSegmentInitializer.initIdSegment(missingDistributor.getNamespacedName(), 0);
        long id = missingDistributor.nextMaxId(100);
        Assertions.assertEquals(100, id);
        Assertions.assertThrows(SQLIntegrityConstraintViolationException.class, () -> mySqlIdSegmentInitializer.initIdSegment(missingDistributor.getNamespacedName(), 0));
    }
    
    @SneakyThrows
    @Test
    public void concurrent_generate_step_100() {
        String namespace = UUID.randomUUID().toString();
        JdbcIdSegmentDistributor maxIdDistributorGenerateStep100 = new JdbcIdSegmentDistributor(namespace, MockIdGenerator.INSTANCE.generateAsString(), 100, dataSource);
        mySqlIdSegmentInitializer.initIdSegment(maxIdDistributorGenerateStep100.getNamespacedName(), 0);
        SegmentId segmentChainId = new SegmentChainId(maxIdDistributorGenerateStep100);
        new ConcurrentGenerateSpec(segmentChainId).verify();
    }
    
    @SneakyThrows
    @Test
    void nextMaxIdWhenConcurrent() {
        int times = 100;
        CompletableFuture<Long>[] results = new CompletableFuture[100];
        JdbcIdSegmentDistributor idSegmentDistributor = new JdbcIdSegmentDistributor("JdbcIdSegmentDistributorTest", MockIdGenerator.INSTANCE.generateAsString(), 10, dataSource);
        mySqlIdSegmentInitializer.initIdSegment(idSegmentDistributor.getNamespacedName(), 0);
        for (int i = 0; i < times; i++) {
            results[i] = CompletableFuture.supplyAsync(() -> idSegmentDistributor.nextMaxId(1));
        }
        
        CompletableFuture.allOf(results).join();
        
        Long[] machineIds = Arrays.stream(results).map(CompletableFuture::join).sorted().toArray(Long[]::new);
        for (int i = 0; i < machineIds.length; i++) {
            Assertions.assertEquals(i + 1, machineIds[i]);
        }
    }
    
}
