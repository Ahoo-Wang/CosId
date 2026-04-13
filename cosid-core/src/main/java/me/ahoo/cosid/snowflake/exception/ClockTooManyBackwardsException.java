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

package me.ahoo.cosid.snowflake.exception;

import me.ahoo.cosid.CosIdException;

/**
 * Exception thrown when clock backwards exceeds threshold.
 *
 * <p>Indicates that the system clock has moved backwards by more than
 * the configured broken threshold, and the generator cannot recover.
 *
 * @author ahoo wang
 */
public class ClockTooManyBackwardsException extends CosIdException {

    private final long lastTimestamp;
    private final long currentTimestamp;
    private final long brokenThreshold;

    /**
     * Creates a new exception.
     *
     * @param lastTimestamp    the last generated timestamp
     * @param currentTimestamp the current system timestamp
     * @param brokenThreshold the configured broken threshold
     */
    public ClockTooManyBackwardsException(long lastTimestamp, long currentTimestamp, long brokenThreshold) {
        super(String.format("Clock moved backwards too many.  brokenThreshold:[%s] | lastTimestamp:[%s] | currentTimestamp:[%s]", brokenThreshold, lastTimestamp, currentTimestamp));
        this.lastTimestamp = lastTimestamp;
        this.currentTimestamp = currentTimestamp;
        this.brokenThreshold = brokenThreshold;
    }

    /**
     * Gets the last timestamp.
     *
     * @return the last timestamp
     */
    public long getLastTimestamp() {
        return lastTimestamp;
    }

    /**
     * Gets the current timestamp.
     *
     * @return the current timestamp
     */
    public long getCurrentTimestamp() {
        return currentTimestamp;
    }

    /**
     * Gets the broken threshold.
     *
     * @return the broken threshold
     */
    public long getBrokenThreshold() {
        return brokenThreshold;
    }
}
