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

package me.ahoo.cosid.store;

import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.segment.RangeStore;

/**
 * An open store: its range and slot lease primitives (06-stores.md §1). Closing it releases what it created itself,
 * never an injected client.
 */
public interface Store extends AutoCloseable {
    /**
     * The range primitive.
     *
     * @return the range store
     * @throws UnsupportedOperationException if the backend has none
     */
    RangeStore ranges();

    /**
     * The slot lease primitive.
     *
     * @return the slot lease store
     * @throws UnsupportedOperationException if the backend has none
     */
    SlotLeaseStore slots();

    @Override
    void close();
}
