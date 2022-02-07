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

package me.ahoo.cosid.shardingsphere.sharding.interval;

import me.ahoo.cosid.CosIdException;

/**
 * Not Support Interval Sharding Type Exception.
 *
 * @author ahoo wang
 */
public class NotSupportIntervalShardingTypeException extends CosIdException {

    public NotSupportIntervalShardingTypeException() {
    }

    public NotSupportIntervalShardingTypeException(String message) {
        super(message);
    }

    public NotSupportIntervalShardingTypeException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotSupportIntervalShardingTypeException(Throwable cause) {
        super(cause);
    }

    public NotSupportIntervalShardingTypeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
