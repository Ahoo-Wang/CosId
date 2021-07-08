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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
class DefaultSegmentIdTest {

    @Test
    void generate() {
        DefaultSegmentId defaultSegmentId = new DefaultSegmentId(new IdSegmentDistributor.Mock());
        defaultSegmentId.generate();
    }

    static final int CONCURRENT_THREADS = 20;
    static final int THREAD_REQUEST_NUM = 50000;

    @Test
    public void concurrent_generate() {
        DefaultSegmentId defaultSegmentId = new DefaultSegmentId(new IdSegmentDistributor.Mock());
        CompletableFuture<List<Long>>[] completableFutures = new CompletableFuture[CONCURRENT_THREADS];
        int threads = 0;
        while (threads < CONCURRENT_THREADS) {
            completableFutures[threads] = CompletableFuture.supplyAsync(() -> {
                List<Long> ids = new ArrayList<>(THREAD_REQUEST_NUM);
                int requestNum = 0;
                long lastId = 0;
                while (requestNum < THREAD_REQUEST_NUM) {
                    requestNum++;
                    long id = defaultSegmentId.generate();
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

            Assertions.assertEquals(THREAD_REQUEST_NUM * CONCURRENT_THREADS, lastId);
        }).join();
    }

    static final int MULTI_CONCURRENT_THREADS = 10;
    static final int MULTI_THREAD_REQUEST_NUM = 50000;

    @Test
    public void concurrent_generate_multi_instance() {

        IdSegmentDistributor testMaxIdDistributor = new IdSegmentDistributor.Mock();
        DefaultSegmentId defaultSegmentId1 = new DefaultSegmentId(testMaxIdDistributor);
        DefaultSegmentId defaultSegmentId2 = new DefaultSegmentId(testMaxIdDistributor);
        CompletableFuture<List<Long>>[] completableFutures = new CompletableFuture[MULTI_CONCURRENT_THREADS * 2];
        int threads1 = 0;
        while (threads1 < MULTI_CONCURRENT_THREADS) {
            completableFutures[threads1] = CompletableFuture.supplyAsync(() -> {
                List<Long> ids = new ArrayList<>(MULTI_THREAD_REQUEST_NUM);
                int requestNum = 0;
                long lastId = 0;
                while (requestNum < MULTI_THREAD_REQUEST_NUM) {
                    requestNum++;
                    long id = defaultSegmentId1.generate();
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
                long lastId = 0;
                while (requestNum < MULTI_THREAD_REQUEST_NUM) {
                    requestNum++;
                    long id = defaultSegmentId2.generate();
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

                Assertions.assertEquals(lastId + 1, currentId);
                lastId = currentId;
            }

            Assertions.assertEquals(MULTI_THREAD_REQUEST_NUM * MULTI_CONCURRENT_THREADS * 2, lastId);
        }).join();
    }

}
