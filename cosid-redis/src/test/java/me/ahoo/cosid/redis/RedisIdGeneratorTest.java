package me.ahoo.cosid.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;

import me.ahoo.cosid.IdGenerator;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;


/**
 * @author ahoo wang
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RedisIdGeneratorTest {
    protected RedisClient redisClient;
    protected StatefulRedisConnection<String, String> redisConnection;
    protected RedisIdGenerator redisIdGenerator;

    @BeforeAll
    private void initRedis() {
        System.out.println("--- initRedis ---");
        redisClient = RedisClient.create("redis://localhost:6379");
        redisConnection = redisClient.connect();

    }

    @Test
    public void generateOnce() {
        long id = redisIdGenerator.generate();
        Assertions.assertTrue(id > 0);
    }

    @Test
    public void generate() {
        long id = redisIdGenerator.generate();
        Assertions.assertTrue(id > 0);

        long id2 = redisIdGenerator.generate();
        Assertions.assertTrue(id2 > 0);
        Assertions.assertTrue(id2 > id);

        long id3 = redisIdGenerator.generate();
        Assertions.assertTrue(id3 > 0);
        Assertions.assertTrue(id3 > id2);
    }

    static final int CONCURRENT_THREADS = 20;
    static final int THREAD_REQUEST_NUM = 50000;

    @Test
    public void concurrent_generate_step_100() {
        String namespace = UUID.randomUUID().toString();
        redisIdGenerator = new RedisIdGenerator(namespace, "generate_step_10", 100, redisClient.connect().async());
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CompletableFuture<List<Long>>[] completableFutures = new CompletableFuture[CONCURRENT_THREADS];
        int threads = 0;
        while (threads < CONCURRENT_THREADS) {
            completableFutures[threads] = CompletableFuture.supplyAsync(() -> {
                List<Long> ids = new ArrayList<>(THREAD_REQUEST_NUM);
                int requestNum = 0;
                while (requestNum < THREAD_REQUEST_NUM) {
                    requestNum++;
                    long id = redisIdGenerator.generate();
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
        executorService.shutdown();
    }

    static final int MULTI_CONCURRENT_THREADS = 15;
    static final int MULTI_THREAD_REQUEST_NUM = 50000;

    @Test
    public void concurrent_generate_step_10_multi_instance() {
        String namespace = UUID.randomUUID().toString();
        IdGenerator idGenerator1 = new RedisIdGenerator(namespace, "generate_step_10", 10, redisClient.connect().async());
        IdGenerator idGenerator2 = new RedisIdGenerator(namespace, "generate_step_10", 10, redisClient.connect().async());
        ExecutorService executorService = Executors.newFixedThreadPool(10);
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
        executorService.shutdown();
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
