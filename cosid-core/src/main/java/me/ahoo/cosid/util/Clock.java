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

import com.google.errorprone.annotations.ThreadSafe;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

/**
 * {@link System#currentTimeMillis()} is too slow.
 *
 * @author ahoo wang
 */
@ThreadSafe
@FunctionalInterface
public interface Clock {

    Clock SYSTEM = new SystemClock();
    Clock CACHE = new CacheClock(SYSTEM);

    long secondTime();

    static long getSystemSecondTime() {
        return System.currentTimeMillis() / 1000;
    }

    class SystemClock implements Clock {

        /**
         * {@link System#currentTimeMillis()} is too slow.
         *
         * @return second time from cache
         */
        @Override
        public long secondTime() {
            return getSystemSecondTime();
        }
    }

    /**
     * Fix the problem that {@link System#currentTimeMillis()} is too slow.
     * The accuracy is 1 second
     *
     * @author ahoo wang
     */
    class CacheClock implements Clock, Runnable {
        /**
         * Tolerate a one-second time limit.
         */
        public static final long ONE_SECOND_PERIOD = Duration.ofSeconds(1).toNanos();
        private final Clock clock;
        private final Thread thread;
        private volatile long lastTime;

        public CacheClock(Clock clock) {
            this.clock = clock;
            this.lastTime = clock.secondTime();
            this.thread = new Thread(this);
            this.thread.setName("CosId-CacheClock");
            this.thread.setDaemon(true);
            this.thread.start();
        }

        @Override
        public long secondTime() {
            return lastTime;
        }

        @Override
        public void run() {
            while (!thread.isInterrupted()) {
                long currentTime = clock.secondTime();
                // Avoid time going backwards
                if (currentTime > lastTime) {
                    this.lastTime = currentTime;
                }
                LockSupport.parkNanos(this, ONE_SECOND_PERIOD);
            }
        }
    }
}
