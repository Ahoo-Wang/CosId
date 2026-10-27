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

package me.ahoo.cosid.store.tck;

import me.ahoo.cosid.lease.SlotLeaseStore;

import java.time.Duration;
import java.util.Optional;

/**
 * The in-memory store passes the slot lease contract on a clock the test moves: the expiry cases jump the clock
 * instead of waiting, and the clock cases check storeNow exactly.
 */
class InMemorySlotLeaseStoreManualClockTest extends SlotLeaseStoreContract {
    private final ManualClock clock = new ManualClock(System.currentTimeMillis() - Duration.ofDays(1).toMillis());
    private final InMemoryStore store = new InMemoryStore(clock);

    @Override
    protected SlotLeaseStore newStore() {
        return store;
    }

    @Override
    protected Optional<ManualClock> storeClock() {
        return Optional.of(clock);
    }
}
