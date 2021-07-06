package me.ahoo.cosid.segment;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author ahoo wang
 */
class IdSegmentTest {

    static final int CONCURRENT_THREADS = 20;

    @Test
    void getAndIncrement() {
        int maxId = 10000000;
        DefaultIdSegment segment = new DefaultIdSegment(maxId, maxId);

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CompletableFuture<List<Long>>[] completableFutures = new CompletableFuture[CONCURRENT_THREADS];
        int threads = 0;
        while (threads < CONCURRENT_THREADS) {
            completableFutures[threads] = CompletableFuture.supplyAsync(() -> {
                int idsSize = maxId / CONCURRENT_THREADS;
                List<Long> ids = new ArrayList<>(idsSize);
                int requestNum = 0;
                long lastId = 0;
                while (requestNum < idsSize) {
                    requestNum++;
                    long id = segment.incrementAndGet();
                    ids.add(id);
                    Assertions.assertTrue(lastId < id);
                    lastId = id;
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

            Assertions.assertEquals(maxId, lastId);
        }).join();
        executorService.shutdown();
    }

}
