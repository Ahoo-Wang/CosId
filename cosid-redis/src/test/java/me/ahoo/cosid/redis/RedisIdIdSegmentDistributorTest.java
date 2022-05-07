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

package me.ahoo.cosid.redis;

import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.SegmentId;
import me.ahoo.cosid.test.ConcurrentGenerateSpec;
import me.ahoo.cosid.test.MockIdGenerator;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Objects;


/**
 * @author ahoo wang
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RedisIdIdSegmentDistributorTest {
    protected RedisClient redisClient;
    protected StatefulRedisConnection<String, String> redisConnection;
    protected RedisIdSegmentDistributor redisMaxIdDistributor;
    
    @BeforeAll
    private void initRedis() {
        System.out.println("--- initRedis ---");
        redisClient = RedisClient.create("redis://localhost:6379");
        redisConnection = redisClient.connect();
        redisMaxIdDistributor =
            new RedisIdSegmentDistributor(MockIdGenerator.INSTANCE.generateAsString(), "RedisIdGeneratorTest", 0, 100, RedisIdSegmentDistributor.DEFAULT_TIMEOUT, redisClient.connect().reactive());
    }
    
    @Test
    public void nextMaxId() {
        long nextMaxId = new RedisIdSegmentDistributor(MockIdGenerator.INSTANCE.generateAsString(), "nextMaxId", 0, 100, RedisIdSegmentDistributor.DEFAULT_TIMEOUT, redisClient.connect().reactive())
            .nextMaxId();
        Assertions.assertEquals(100, nextMaxId);
    }
    
    @Test
    public void generate() {
        long id = redisMaxIdDistributor.nextMaxId();
        Assertions.assertTrue(id > 0);
        
        long id2 = redisMaxIdDistributor.nextMaxId();
        Assertions.assertTrue(id2 > 0);
        Assertions.assertTrue(id2 > id);
        
        long id3 = redisMaxIdDistributor.nextMaxId();
        Assertions.assertTrue(id3 > 0);
        Assertions.assertTrue(id3 > id2);
    }
    
    @Test
    public void generateWhenMaxIdBack() {
        long id = redisMaxIdDistributor.nextMaxId();
        Assertions.assertTrue(id > 0);
        String adderKey = redisMaxIdDistributor.getAdderKey();
        redisConnection.sync().set(adderKey, String.valueOf(id - 1));
        Assertions.assertThrows(IllegalStateException.class, () -> redisMaxIdDistributor.nextMaxId());
    }
    
    @Test
    public void generateWhenOffset10() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        RedisIdSegmentDistributor redisMaxIdDistributorOffset10 =
            new RedisIdSegmentDistributor(namespace, "generate_offset", 10, 100, RedisIdSegmentDistributor.DEFAULT_TIMEOUT, redisClient.connect().reactive());
        long id = redisMaxIdDistributorOffset10.nextMaxId();
        Assertions.assertEquals(110, id);
    }
    
    @Test
    public void generateWhenConcurrent() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        RedisIdSegmentDistributor redisMaxIdDistributorGenerateStep100 =
            new RedisIdSegmentDistributor(namespace, "Concurrent", 0, 100, RedisIdSegmentDistributor.DEFAULT_TIMEOUT, redisClient.connect().reactive());
        SegmentId defaultSegmentId = new DefaultSegmentId(redisMaxIdDistributorGenerateStep100);
        new ConcurrentGenerateSpec(defaultSegmentId).verify();
    }
    
    @Test
    public void generateWhenMultiInstanceConcurrent() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        RedisIdSegmentDistributor redisMaxIdDistributor1 =
            new RedisIdSegmentDistributor(namespace, "MultiInstanceConcurrent", 0, 100, RedisIdSegmentDistributor.DEFAULT_TIMEOUT, redisClient.connect().reactive());
        RedisIdSegmentDistributor redisMaxIdDistributor2 =
            new RedisIdSegmentDistributor(namespace, "MultiInstanceConcurrent", 0, 100, RedisIdSegmentDistributor.DEFAULT_TIMEOUT, redisClient.connect().reactive());
        SegmentId idGenerator1 = new DefaultSegmentId(redisMaxIdDistributor1);
        SegmentId idGenerator2 = new DefaultSegmentId(redisMaxIdDistributor2);
        new ConcurrentGenerateSpec(idGenerator1, idGenerator2).verify();
    }
    
    @AfterAll
    private void destroyRedis() {
        System.out.println("--- destroyRedis ---");
        
        if (Objects.nonNull(redisConnection)) {
            redisConnection.close();
        }
        if (Objects.nonNull(redisClient)) {
            redisClient.shutdown();
        }
    }
}
