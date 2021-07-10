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

package me.ahoo.cosid.segment;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static me.ahoo.cosid.segment.IdSegment.TIME_TO_LIVE_FOREVER;

/**
 * @author ahoo wang
 */
class SegmentChainIdTest {

    @Test
    @SneakyThrows
    void sort() {
        IdSegmentDistributor idSegmentDistributor = new IdSegmentDistributor.Atomic();
        IdSegmentChain idSegmentClain1 = idSegmentDistributor.nextIdSegmentChain(IdSegmentChain.newRoot());
        IdSegmentChain idSegmentClain2 = idSegmentDistributor.nextIdSegmentChain(IdSegmentChain.newRoot());
        IdSegmentChain idSegmentClain3 = idSegmentDistributor.nextIdSegmentChain(IdSegmentChain.newRoot());
        List<IdSegmentChain> chainList = Arrays.asList(idSegmentClain2, idSegmentClain1, idSegmentClain3);
        chainList.sort(null);
        Assertions.assertEquals(idSegmentClain1, chainList.get(0));
        Assertions.assertEquals(idSegmentClain2, chainList.get(1));
        Assertions.assertEquals(idSegmentClain3, chainList.get(2));
    }

    @Test
    @SneakyThrows
    void nextIdSegmentsClain() {
        IdSegmentDistributor idSegmentDistributor = new IdSegmentDistributor.Atomic();
        IdSegmentChain rootClain = idSegmentDistributor.nextIdSegmentChain(IdSegmentChain.newRoot(), 3);
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
        SegmentChainId segmentChainId = new SegmentChainId(TIME_TO_LIVE_FOREVER,10, SegmentChainId.DEFAULT_PREFETCH_PERIOD, new IdSegmentDistributor.Atomic(2));
//        Thread.sleep(10);
        segmentChainId.generate();
        segmentChainId.generate();
        segmentChainId.generate();
    }

    static final int CONCURRENT_THREADS = 20;
    static final int THREAD_REQUEST_NUM = 50000;

    @Test
    public void concurrent_generate() {
        SegmentChainId segmentChainId = new SegmentChainId(new IdSegmentDistributor.Mock());
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
                /**
                 * 单实例下可以保证绝对递增+1，不存在ID间隙
                 */
                Assertions.assertEquals(lastId + 1, currentId);
                lastId = currentId;
            }
            Assertions.assertTrue(THREAD_REQUEST_NUM * CONCURRENT_THREADS <= lastId);
            Assertions.assertEquals(THREAD_REQUEST_NUM * CONCURRENT_THREADS, lastId);
        }).join();
    }

    static final int MULTI_CONCURRENT_THREADS = 10;
    static final int MULTI_THREAD_REQUEST_NUM = 10;

    @Test
    public void concurrent_generate_multi_instance() {

        IdSegmentDistributor testMaxIdDistributor = new IdSegmentDistributor.Mock();
        SegmentChainId segmentChainId1 = new SegmentChainId(testMaxIdDistributor);
        SegmentChainId segmentChainId2 = new SegmentChainId(testMaxIdDistributor);
        final IdSegmentChain head1 = segmentChainId1.getHead();
        final IdSegmentChain head2 = segmentChainId2.getHead();
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
            ArrayList<IdSegment> idSegments = new ArrayList<>(totalIds.size() / testMaxIdDistributor.getStep() + 1000);
            IdSegmentChain current = head1;
            while (current.getNext() != null) {
                current = current.getNext();
                idSegments.add(current.getIdSegment());
            }
            current = head2;
            while (current.getNext() != null) {
                current = current.getNext();
                idSegments.add(current.getIdSegment());
            }
            idSegments.sort(null);
            for (int i = 1; i < idSegments.size(); i++) {
                IdSegment pre = idSegments.get(i - 1);
                IdSegment next = idSegments.get(i);
                if (pre.getOffset() + pre.getStep() != next.getOffset()) {
                    throw new NextIdSegmentExpiredException(pre, next);
                }
            }

            Long lastId = null;
            for (Long currentId : totalIds) {
                if (lastId == null) {
                    Assertions.assertEquals(1, currentId);
                    lastId = currentId;
                    continue;
                }
//                if (lastId + 1 != currentId) {
//                    /**
//                     * SegmentChainId 预取（安全间隙规则）导致实例1/实例2 预取到的IdSegment没有完全使用，导致ID空洞，只能保证趋势递增
//                     */
//                    Assertions.assertEquals(lastId + 1, currentId);
//                }
                Assertions.assertTrue(lastId + 1 <= currentId);

                lastId = currentId;
            }

            Assertions.assertTrue(MULTI_THREAD_REQUEST_NUM * MULTI_CONCURRENT_THREADS * 2 <= lastId);
        }).join();
    }
}
