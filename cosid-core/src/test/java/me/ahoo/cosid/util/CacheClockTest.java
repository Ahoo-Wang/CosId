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

import static me.ahoo.cosid.util.Clock.CacheClock.ONE_SECOND_PERIOD;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;


/**
 * @author ahoo wang
 */
class CacheClockTest {

    @Test
    void secondTime() {
        long actual = Clock.CACHE.secondTime();
        long expected = Clock.getSystemSecondTime();
        long diff = Math.abs(actual - expected);
        long tolerance = 1L;
        assertTrue(diff <= tolerance);
    }

    @SneakyThrows
    @Test
    void secondTimeWhenSleep() {
        TimeUnit.SECONDS.sleep(1);
        long actual = Clock.CACHE.secondTime();
        long expected = Clock.getSystemSecondTime();
        long diff = Math.abs(actual - expected);
        long tolerance = 1L;
        assertTrue(diff <= tolerance);
    }

    @Test
    void secondTimeIfBackwards() {
        Clock backwardsClock = new BackwardsClock();
        Clock cacheClock = new Clock.CacheClock(backwardsClock);
        long lastTime = cacheClock.secondTime();
        for (int i = 0; i < 6; i++) {
            long currentTime = cacheClock.secondTime();
            assertTrue(currentTime >= lastTime);
            lastTime = currentTime;
            LockSupport.parkNanos(this, ONE_SECOND_PERIOD);
        }
    }

    static class BackwardsClock implements Clock {
        private final long[] timeline = new long[]{1, 2, 3, 4, 5};
        private int index = 0;

        @Override
        public long secondTime() {
            int idx = Math.floorMod(index++, timeline.length);
            return timeline[idx];
        }
    }
}
