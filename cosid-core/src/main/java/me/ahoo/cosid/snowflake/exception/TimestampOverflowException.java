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

import com.google.common.base.Strings;

/**
 * Exception thrown when timestamp exceeds maximum value.
 *
 * <p>Indicates that the timestamp portion of the ID has overflowed,
 * meaning the generator has been in use for longer than the configured
 * time range allows.
 *
 * @author ahoo wang
 */
public class TimestampOverflowException extends CosIdException {
    private final long epoch;
    private final long diffTimestamp;
    private final long maxTimestamp;

    /**
     * Creates a new exception.
     *
     * @param epoch          the configured epoch
     * @param diffTimestamp  the calculated timestamp difference
     * @param maxTimestamp   the maximum representable timestamp
     */
    public TimestampOverflowException(long epoch, long diffTimestamp, long maxTimestamp) {
        super(Strings.lenientFormat("epoch:[%s] - diffTimestamp:[%s] can't be greater than maxTimestamp:[%s]", epoch, diffTimestamp, maxTimestamp));
        this.epoch = epoch;
        this.diffTimestamp = diffTimestamp;
        this.maxTimestamp = maxTimestamp;
    }

    /**
     * Gets the epoch.
     *
     * @return the epoch
     */
    public long getEpoch() {
        return epoch;
    }

    /**
     * Gets the diff timestamp.
     *
     * @return the diff timestamp
     */
    public long getDiffTimestamp() {
        return diffTimestamp;
    }

    /**
     * Gets the max timestamp.
     *
     * @return the max timestamp
     */
    public long getMaxTimestamp() {
        return maxTimestamp;
    }
}
