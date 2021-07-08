/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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
 * @author ahoo wang
 */
public class ClockTooManyBackwardsException extends CosIdException {

    private final long lastTimestamp;
    private final long currentTimestamp;
    private final long brokenThreshold;

    public ClockTooManyBackwardsException(long lastTimestamp, long currentTimestamp, long brokenThreshold) {
        super(String.format("Clock moved backwards too many.  brokenThreshold:[%s] | lastTimestamp:[%s] | currentTimestamp:[%s]", brokenThreshold, lastTimestamp, currentTimestamp));
        this.lastTimestamp = lastTimestamp;
        this.currentTimestamp = currentTimestamp;
        this.brokenThreshold = brokenThreshold;
    }

    public long getLastTimestamp() {
        return lastTimestamp;
    }

    public long getCurrentTimestamp() {
        return currentTimestamp;
    }

    public long getBrokenThreshold() {
        return brokenThreshold;
    }
}
