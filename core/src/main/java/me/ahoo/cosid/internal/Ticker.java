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

package me.ahoo.cosid.internal;

import java.util.concurrent.TimeUnit;

/**
 * Elapsed time for the schedules of the lease and window keepers: monotonic, unlike the wall clock the ids use, so a
 * clock step neither stalls nor rushes a renewal. Tests drive a manual one.
 */
public interface Ticker {
    Ticker SYSTEM = new Ticker() {
        @Override
        public long nanoTime() {
            return System.nanoTime();
        }

        @Override
        public void sleep(long nanos) throws InterruptedException {
            TimeUnit.NANOSECONDS.sleep(nanos);
        }
    };

    /**
     * The current tick.
     *
     * @return nanoseconds from an arbitrary origin
     */
    long nanoTime();

    /**
     * Waits {@code nanos}.
     *
     * @param nanos how long to wait
     * @throws InterruptedException if interrupted
     */
    void sleep(long nanos) throws InterruptedException;
}
