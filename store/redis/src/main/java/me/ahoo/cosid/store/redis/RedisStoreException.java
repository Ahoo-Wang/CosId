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

package me.ahoo.cosid.store.redis;

import me.ahoo.cosid.CosIdException;

/**
 * A Redis store operation failed: Redis returned an error, did not answer within the store timeout, or the replicas
 * did not acknowledge the write ({@code WAIT}, 06-stores.md §2.2). The outcome of the write is unknown: the caller
 * must not use a range or ceiling it did not receive, and may retry (05-machine-lease.md §2.2: renew and acquire are
 * idempotent; a lost range is a gap, D19).
 */
public class RedisStoreException extends CosIdException {
    public RedisStoreException(String message) {
        super(message);
    }

    public RedisStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
