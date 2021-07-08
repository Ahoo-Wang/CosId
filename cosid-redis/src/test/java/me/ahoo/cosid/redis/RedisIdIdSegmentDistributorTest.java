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

package me.ahoo.cosid.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.SegmentId;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


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
        redisMaxIdDistributor = new RedisIdSegmentDistributor(UUID.randomUUID().toString(), "RedisIdGeneratorTest", 0, 100, RedisIdSegmentDistributor.DEFAULT_TIMEOUT, redisClient.connect().async());
    }

    @Test
    public void nextMaxId() {
        long nextMaxId = redisMaxIdDistributor.nextMaxId();
        Assertions.assertTrue(nextMaxId > 0);
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
    public void generate_offset() {
        String namespace = UUID.randomUUID().toString();
        RedisIdSegmentDistributor redisMaxIdDistributor_offset_10 = new RedisIdSegmentDistributor(namespace, "generate_offset", 10, 100, RedisIdSegmentDistributor.DEFAULT_TIMEOUT, redisClient.connect().async());
        long id = redisMaxIdDistributor_offset_10.nextMaxId();
        Assertions.assertEquals(110, id);
    }

    static final int CONCURRENT_THREADS = 20;
    static final int THREAD_REQUEST_NUM = 50000;

    @Test
    public void concurrent_generate_step_100() {
        String namespace = UUID.randomUUID().toString();
        RedisIdSegmentDistributor redisMaxIdDistributor_generate_step_100 = new RedisIdSegmentDistributor(namespace, "generate_step_10", 0, 100, RedisIdSegmentDistributor.DEFAULT_TIMEOUT, redisClient.connect().async());
        SegmentId defaultSegmentId = new DefaultSegmentId(redisMaxIdDistributor_generate_step_100);
        CompletableFuture<List<Long>>[] completableFutures = new CompletableFuture[CONCURRENT_THREADS];
        int threads = 0;
        while (threads < CONCURRENT_THREADS) {
            completableFutures[threads] = CompletableFuture.supplyAsync(() -> {
                List<Long> ids = new ArrayList<>(THREAD_REQUEST_NUM);
                int requestNum = 0;
                while (requestNum < THREAD_REQUEST_NUM) {
                    requestNum++;
                    long id = defaultSegmentId.generate();
                    ids.add(id);
                }
                return ids;
            });

            threads++;
        }
        CompletableFuture.allOf(completableFutures).thenAccept(nil -> {
            List<Long> totalIds = new ArrayList<>();
            for (CompletableFuture<List<Long>> completableFuture : completableFutures) {
                List<Long> ids = completableFuture.join();
                totalIds.addAll(ids);
            }
            totalIds.sort(Long::compareTo);
            Long lastId = null;
            for (Long currentId : totalIds) {
                if (lastId == null) {
                    Assertions.assertEquals(1, currentId);
                    lastId = currentId;
                    continue;
                }

                Assertions.assertEquals(lastId + 1, currentId);
                lastId = currentId;
            }

            Assertions.assertEquals(THREAD_REQUEST_NUM * CONCURRENT_THREADS, lastId);
        }).join();
    }

    static final int MULTI_CONCURRENT_THREADS = 10;
    static final int MULTI_THREAD_REQUEST_NUM = 50000;

    @Test
    public void concurrent_generate_step_10_multi_instance() {
        String namespace = UUID.randomUUID().toString();
        RedisIdSegmentDistributor redisMaxIdDistributor1 = new RedisIdSegmentDistributor(namespace, "generate_step_10", 0, 10, RedisIdSegmentDistributor.DEFAULT_TIMEOUT, redisClient.connect().async());
        RedisIdSegmentDistributor redisMaxIdDistributor2 = new RedisIdSegmentDistributor(namespace, "generate_step_10", 0, 10, RedisIdSegmentDistributor.DEFAULT_TIMEOUT, redisClient.connect().async());
        SegmentId idGenerator1 = new DefaultSegmentId(redisMaxIdDistributor1);
        SegmentId idGenerator2 = new DefaultSegmentId(redisMaxIdDistributor2);

        CompletableFuture<List<Long>>[] completableFutures = new CompletableFuture[MULTI_CONCURRENT_THREADS * 2];
        int threads1 = 0;
        while (threads1 < MULTI_CONCURRENT_THREADS) {
            completableFutures[threads1] = CompletableFuture.supplyAsync(() -> {
                List<Long> ids = new ArrayList<>(MULTI_THREAD_REQUEST_NUM);
                int requestNum = 0;
                while (requestNum < MULTI_THREAD_REQUEST_NUM) {
                    requestNum++;
                    long id = idGenerator1.generate();
                    ids.add(id);
                }
                return ids;
            });
            threads1++;
        }
        int threads2 = threads1;
        while (threads2 < MULTI_CONCURRENT_THREADS * 2) {
            completableFutures[threads2] = CompletableFuture.supplyAsync(() -> {
                List<Long> ids = new ArrayList<>(MULTI_THREAD_REQUEST_NUM);
                int requestNum = 0;
                while (requestNum < MULTI_THREAD_REQUEST_NUM) {
                    requestNum++;
                    long id = idGenerator2.generate();
                    ids.add(id);
                }
                return ids;
            });
            threads2++;
        }
        CompletableFuture.allOf(completableFutures).thenAccept(nil -> {
            List<Long> totalIds = new ArrayList<>();
            for (CompletableFuture<List<Long>> completableFuture : completableFutures) {
                List<Long> ids = completableFuture.join();
                totalIds.addAll(ids);
            }
            totalIds.sort(Long::compareTo);
            Long lastId = null;
            for (Long currentId : totalIds) {
                if (lastId == null) {
                    Assertions.assertEquals(1, currentId);
                    lastId = currentId;
                    continue;
                }

                Assertions.assertEquals(lastId + 1, currentId);
                lastId = currentId;
            }

            Assertions.assertEquals(MULTI_THREAD_REQUEST_NUM * MULTI_CONCURRENT_THREADS * 2, lastId);
        }).join();
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
