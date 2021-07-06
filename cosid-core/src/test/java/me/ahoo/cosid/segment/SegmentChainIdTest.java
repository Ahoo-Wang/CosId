package me.ahoo.cosid.segment;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.AssertionsKt;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * @author ahoo wang
 */
class SegmentChainIdTest {

    @Test
    @SneakyThrows
    void sort() {
        IdSegmentDistributor idSegmentDistributor = new IdSegmentDistributor.JdkIdSegmentDistributor();
        IdSegmentClain idSegmentClain1 = idSegmentDistributor.nextIdSegmentClain(IdSegmentClain.newRoot());
        IdSegmentClain idSegmentClain2 = idSegmentDistributor.nextIdSegmentClain(IdSegmentClain.newRoot());
        IdSegmentClain idSegmentClain3 = idSegmentDistributor.nextIdSegmentClain(IdSegmentClain.newRoot());
        List<IdSegmentClain> clainList = Arrays.asList(idSegmentClain2, idSegmentClain1, idSegmentClain3);
        clainList.sort(null);
        Assertions.assertEquals(idSegmentClain1, clainList.get(0));
        Assertions.assertEquals(idSegmentClain2, clainList.get(1));
        Assertions.assertEquals(idSegmentClain3, clainList.get(2));
    }

    @Test
    @SneakyThrows
    void nextIdSegmentsClain() {
        IdSegmentDistributor idSegmentDistributor = new IdSegmentDistributor.JdkIdSegmentDistributor();
        IdSegmentClain rootClain = idSegmentDistributor.nextIdSegmentClain(IdSegmentClain.newRoot(), 3);
        Assertions.assertEquals(0, rootClain.getVersion());
        Assertions.assertEquals(0, rootClain.getIdSegment().getOffset());
        Assertions.assertEquals(1, rootClain.getNext().getVersion());
        Assertions.assertEquals(idSegmentDistributor.getStep(), rootClain.getNext().getIdSegment().getOffset());
        Assertions.assertEquals(2, rootClain.getNext().getNext().getVersion());
        Assertions.assertEquals(idSegmentDistributor.getStep() * 2, rootClain.getNext().getNext().getIdSegment().getOffset());
        Assertions.assertNull(rootClain.getNext().getNext().getNext());
    }


    @Test
    @SneakyThrows
    void generate() {
        SegmentChainId segmentChainId = new SegmentChainId(10, SegmentChainId.DEFAULT_PREFETCH_PERIOD, new IdSegmentDistributor.JdkIdSegmentDistributor() {
            @Override
            public int getStep() {
                return 2;
            }
        });
//        Thread.sleep(10);
        segmentChainId.generate();
        segmentChainId.generate();
        segmentChainId.generate();
    }

    static final int CONCURRENT_THREADS = 20;
    static final int THREAD_REQUEST_NUM = 50000;

    @Test
    public void concurrent_generate() {
        SegmentChainId segmentChainId = new SegmentChainId(new DefaultSegmentIdTest.TestIdSegmentDistributor());
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CompletableFuture<List<Long>>[] completableFutures = new CompletableFuture[CONCURRENT_THREADS];
        int threads = 0;
        while (threads < CONCURRENT_THREADS) {
            completableFutures[threads] = CompletableFuture.supplyAsync(() -> {
                List<Long> ids = new ArrayList<>(THREAD_REQUEST_NUM);
                int requestNum = 0;
                long lastId = 0;
                while (requestNum < THREAD_REQUEST_NUM) {
                    requestNum++;
                    long id = segmentChainId.generate();
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
//                Assertions.assertTrue(lastId + 1 <= currentId);
                Assertions.assertEquals(lastId + 1, currentId);
                lastId = currentId;
            }
            Assertions.assertTrue(THREAD_REQUEST_NUM * CONCURRENT_THREADS <= lastId);
            Assertions.assertEquals(THREAD_REQUEST_NUM * CONCURRENT_THREADS, lastId);
        }).join();
        executorService.shutdown();
    }

    static final int MULTI_CONCURRENT_THREADS = 20;
    static final int MULTI_THREAD_REQUEST_NUM = 50000;

    @Test
    public void concurrent_generate_multi_instance() {

        IdSegmentDistributor testMaxIdDistributor = new DefaultSegmentIdTest.TestIdSegmentDistributor();
        SegmentChainId segmentChainId1 = new SegmentChainId(testMaxIdDistributor);
        SegmentChainId segmentChainId2 = new SegmentChainId(testMaxIdDistributor);
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CompletableFuture<List<Long>>[] completableFutures = new CompletableFuture[MULTI_CONCURRENT_THREADS * 2];
        int threads1 = 0;

        while (threads1 < MULTI_CONCURRENT_THREADS) {
            completableFutures[threads1] = CompletableFuture.supplyAsync(() -> {
                List<Long> ids = new ArrayList<>(MULTI_THREAD_REQUEST_NUM);
                int requestNum = 0;
                Long lastId = 0L;
                while (requestNum < MULTI_THREAD_REQUEST_NUM) {
                    requestNum++;
                    long id = segmentChainId1.generate();
                    ids.add(id);
                    Assertions.assertTrue(lastId < id);
                    lastId = id;
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
                Long lastId = 0L;
                while (requestNum < MULTI_THREAD_REQUEST_NUM) {
                    requestNum++;
                    long id = segmentChainId2.generate();
                    ids.add(id);
                    Assertions.assertTrue(lastId < id);
                    lastId = id;
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

                Assertions.assertTrue(lastId + 1 <= currentId);
//                Assertions.assertEquals(lastId + 1, currentId);
                lastId = currentId;
            }

            Assertions.assertTrue(MULTI_THREAD_REQUEST_NUM * MULTI_CONCURRENT_THREADS * 2 <= lastId);
        }).join();
        executorService.shutdown();
    }
}
