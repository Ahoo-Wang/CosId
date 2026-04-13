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
 * Exception thrown when system clock moves backwards.
 *
 * <p>Indicates that the current system time is less than the last timestamp
 * used for ID generation, which could cause ID duplication.
 *
 * @author ahoo wang
 */
public class ClockBackwardsException extends CosIdException {
    private final long lastTimestamp;
    private final long currentTimestamp;

    /**
     * Creates a new exception.
     *
     * @param lastTimestamp     the last generated timestamp
     * @param currentTimestamp the current system timestamp
     */
    public ClockBackwardsException(long lastTimestamp, long currentTimestamp) {
        super(String.format("Clock moved backwards.  Refusing to generate id. lastTimestamp:[%s] | currentTimestamp:[%s]", lastTimestamp, currentTimestamp));
        this.lastTimestamp = lastTimestamp;
        this.currentTimestamp = currentTimestamp;
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
}
