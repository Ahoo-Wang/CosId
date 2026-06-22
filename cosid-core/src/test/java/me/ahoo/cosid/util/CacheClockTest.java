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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;


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

    @Test
    void secondTimeWhenSourceClockAdvances() {
        StepClock sourceClock = new StepClock(10, 11);
        Clock.CacheClock cacheClock = new Clock.CacheClock(sourceClock, false);

        cacheClock.tick();

        assertEquals(11, cacheClock.secondTime());
    }

    @Test
    void secondTimeIfBackwards() {
        StepClock backwardsClock = new StepClock(1, 2, 3, 2, 1, 4);
        Clock.CacheClock cacheClock = new Clock.CacheClock(backwardsClock, false);
        assertEquals(1, cacheClock.secondTime());

        cacheClock.tick();
        assertEquals(2, cacheClock.secondTime());
        cacheClock.tick();
        assertEquals(3, cacheClock.secondTime());
        cacheClock.tick();
        assertEquals(3, cacheClock.secondTime());
        cacheClock.tick();
        assertEquals(3, cacheClock.secondTime());
        cacheClock.tick();
        assertEquals(4, cacheClock.secondTime());
    }

    static class StepClock implements Clock {
        private final long[] timeline;
        private int index = 0;

        StepClock(long... timeline) {
            this.timeline = timeline;
        }

        @Override
        public long secondTime() {
            if (index >= timeline.length) {
                return timeline[timeline.length - 1];
            }
            return timeline[index++];
        }
    }
}
