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

package me.ahoo.cosid.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;

import me.ahoo.cosid.CosIdException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class CallTimeoutTest {
    
    @Test
    void noneRunsOnCallerThread() {
        Thread caller = Thread.currentThread();
        
        assertThat(CallTimeout.of(Duration.ZERO).call("same-thread", () -> Thread.currentThread() == caller), equalTo(true));
        assertThat(CallTimeout.of(null), equalTo(CallTimeout.NONE));
    }
    
    @Test
    void returnsResultWithinTimeout() {
        assertThat(CallTimeout.of(Duration.ofSeconds(5)).call("fast", () -> 42), equalTo(42));
    }
    
    @Test
    void timesOutAndInterruptsTheCall() throws InterruptedException {
        CountDownLatch interrupted = new CountDownLatch(1);
        long startedAt = System.nanoTime();
        
        Assertions.assertThrows(CosIdException.class, () -> CallTimeout.of(Duration.ofMillis(100)).call("hang", () -> {
            try {
                Thread.sleep(Duration.ofMinutes(1).toMillis());
            } catch (InterruptedException interruptedException) {
                interrupted.countDown();
            }
            return null;
        }));
        
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt).compareTo(Duration.ofSeconds(5)), lessThan(0));
        assertThat(interrupted.await(5, TimeUnit.SECONDS), equalTo(true));
    }
    
    @Test
    void rethrowsRuntimeExceptionsAndWrapsCheckedOnes() {
        CallTimeout timeout = CallTimeout.of(Duration.ofSeconds(5));
        
        Assertions.assertThrows(IllegalStateException.class, () -> timeout.call("runtime", () -> {
            throw new IllegalStateException("boom");
        }));
        CosIdException wrapped = Assertions.assertThrows(CosIdException.class, () -> timeout.call("checked", () -> {
            throw new IOException("io");
        }));
        assertThat(wrapped.getCause().getClass(), equalTo(IOException.class));
        Assertions.assertThrows(IllegalStateException.class, () -> CallTimeout.NONE.call("runtime", () -> {
            throw new IllegalStateException("boom");
        }));
    }
    
    @Test
    void rejectsNegativeTimeout() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> CallTimeout.of(Duration.ofSeconds(-1)));
    }
}
