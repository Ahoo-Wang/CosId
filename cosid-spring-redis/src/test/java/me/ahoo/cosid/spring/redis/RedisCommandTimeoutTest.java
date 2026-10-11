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

package me.ahoo.cosid.spring.redis;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

import me.ahoo.cosid.CosIdException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class RedisCommandTimeoutTest {

    @Test
    void ofNullOrZeroIsNone() {
        assertThat(RedisCommandTimeout.of(null), sameInstance(RedisCommandTimeout.NONE));
        assertThat(RedisCommandTimeout.of(Duration.ZERO), sameInstance(RedisCommandTimeout.NONE));
        assertThat(RedisCommandTimeout.NONE.isEnabled(), equalTo(false));
        Assertions.assertThrows(IllegalArgumentException.class, () -> RedisCommandTimeout.of(Duration.ofSeconds(-1)));
    }

    @Test
    void noneRunsOnCallerThread() {
        Thread caller = Thread.currentThread();
        Thread actual = RedisCommandTimeout.NONE.call("op", Thread::currentThread);
        assertThat(actual, sameInstance(caller));
    }

    @Test
    void returnsResultWithinTimeout() {
        RedisCommandTimeout timeout = RedisCommandTimeout.of(Duration.ofSeconds(5));
        Thread actual = timeout.call("op", Thread::currentThread);
        assertThat(actual, not(sameInstance(Thread.currentThread())));
        assertThat(timeout.call("op", () -> 42L), equalTo(42L));
    }

    @Test
    void throwsAndInterruptsWhenCommandHangs() throws InterruptedException {
        RedisCommandTimeout timeout = RedisCommandTimeout.of(Duration.ofMillis(100));
        CountDownLatch interrupted = new CountDownLatch(1);
        long startNanos = System.nanoTime();

        RedisCommandTimeoutException actual = Assertions.assertThrows(RedisCommandTimeoutException.class, () -> timeout.run("guardMachineId", () -> {
            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(1));
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
        }));

        assertThat(System.nanoTime() - startNanos, lessThan(TimeUnit.SECONDS.toNanos(5)));
        assertThat(actual.getOperation(), equalTo("guardMachineId"));
        assertThat(actual.getTimeout(), equalTo(Duration.ofMillis(100)));
        assertThat(interrupted.await(5, TimeUnit.SECONDS), equalTo(true));
    }

    @Test
    void propagatesCommandException() {
        RedisCommandTimeout timeout = RedisCommandTimeout.of(Duration.ofSeconds(5));
        IllegalStateException expected = new IllegalStateException("boom");
        IllegalStateException actual = Assertions.assertThrows(IllegalStateException.class, () -> timeout.call("op", () -> {
            throw expected;
        }));
        assertThat(actual, sameInstance(expected));
    }

    @Test
    void preservesInterruptOfCaller() {
        RedisCommandTimeout timeout = RedisCommandTimeout.of(Duration.ofSeconds(5));
        Thread.currentThread().interrupt();
        try {
            Assertions.assertThrows(CosIdException.class, () -> timeout.run("op", () -> {
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(10));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(Thread.currentThread().isInterrupted(), equalTo(true));
        } finally {
            Thread.interrupted();
        }
    }
}
