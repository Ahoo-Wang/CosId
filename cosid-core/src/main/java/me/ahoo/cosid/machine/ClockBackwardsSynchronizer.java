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

package me.ahoo.cosid.machine;

import me.ahoo.cosid.snowflake.exception.ClockTooManyBackwardsException;

import com.google.errorprone.annotations.ThreadSafe;

/**
 * Synchronizer for handling clock backwards issues in Snowflake ID generation.
 *
 * <p>When system clock moves backwards, this synchronizer waits until
 * the clock catches up to ensure unique IDs.
 *
 * @author ahoo wang
 */
@ThreadSafe
public interface ClockBackwardsSynchronizer {
    /**
     * Default synchronizer instance.
     */
    ClockBackwardsSynchronizer DEFAULT = new DefaultClockBackwardsSynchronizer();

    /**
     * Synchronizes clock by waiting until current time exceeds lastTimestamp.
     *
     * @param lastTimestamp the last timestamp that was generated
     * @throws InterruptedException          if thread is interrupted while waiting
     * @throws ClockTooManyBackwardsException if clock backwards exceeds threshold
     */
    void sync(long lastTimestamp) throws InterruptedException, ClockTooManyBackwardsException;

    /**
     * Synchronizes clock without throwing InterruptedException.
     *
     * @param lastTimestamp the last timestamp that was generated
     * @throws ClockTooManyBackwardsException if clock backwards exceeds threshold
     */
    void syncUninterruptibly(long lastTimestamp) throws ClockTooManyBackwardsException;

    /**
     * Calculates how far backwards the clock has moved.
     *
     * @param lastTimestamp the last timestamp
     * @return the backwards duration in milliseconds
     */
    static long getBackwardsTimeStamp(long lastTimestamp) {
        return lastTimestamp - System.currentTimeMillis();
    }
}

