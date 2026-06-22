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

package me.ahoo.cosid.jvm;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 */
class AtomicLongGeneratorTest {

    @Test
    void generateShouldIncrementByOne() {
        long idFirst = AtomicLongGenerator.INSTANCE.generate();
        long idSecond = AtomicLongGenerator.INSTANCE.generate();

        assertThat(idSecond, equalTo(idFirst + 1));
    }

    @Test
    void generateShouldBeUniqueWhenCalledConcurrently() throws InterruptedException {
        int taskCount = 256;
        ExecutorService executorService = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(taskCount);
        Set<Long> ids = ConcurrentHashMap.newKeySet();

        try {
            for (int i = 0; i < taskCount; i++) {
                executorService.execute(() -> {
                    try {
                        start.await();
                        ids.add(AtomicLongGenerator.INSTANCE.generate());
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();

            Assertions.assertTrue(done.await(5, TimeUnit.SECONDS));
            assertThat(ids.size(), equalTo(taskCount));
        } finally {
            executorService.shutdownNow();
        }
    }
}
