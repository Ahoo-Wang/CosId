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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.store.StoreTimeoutException;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

class RedisStoreExceptionTest {
    @Test
    void aTimeoutCarriesAStoreTimeoutCause() {
        TimeoutException client = new TimeoutException();
        RedisStoreException timeout = RedisStoreException.timeout("Redis did not answer within PT1S", client);
        assertEquals("Redis did not answer within PT1S", timeout.getMessage());
        assertInstanceOf(StoreTimeoutException.class, timeout.getCause());
        assertSame(client, timeout.getCause().getCause());
        assertTrue(StoreTimeoutException.isTimeout(timeout));
        assertFalse(StoreTimeoutException.isTimeout(new RedisStoreException("Redis failed")));
    }
}
