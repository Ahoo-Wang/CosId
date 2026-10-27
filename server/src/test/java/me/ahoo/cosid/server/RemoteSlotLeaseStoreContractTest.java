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

package me.ahoo.cosid.server;

import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.store.tck.InMemoryStore;
import me.ahoo.cosid.store.tck.ManualClock;
import me.ahoo.cosid.store.tck.SlotLeaseStoreContract;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.util.Optional;

/**
 * The slot lease contract through store/remote against a real cosid-server, started in process on
 * {@link InMemoryStore} with a {@link ManualClock}: the store clock is the server's, which the expiry cases move.
 */
class RemoteSlotLeaseStoreContractTest extends SlotLeaseStoreContract {
    private static final ManualClock CLOCK = new ManualClock(System.currentTimeMillis());
    private static TestServer server;

    @BeforeAll
    static void start() {
        server = TestServer.memory(new InMemoryStore(CLOCK), TestServer.CONTRACT_LIMITS);
    }

    @AfterAll
    static void stop() {
        server.close();
    }

    @Override
    protected SlotLeaseStore newStore() {
        return RemoteContractSupport.slots(server);
    }

    @Override
    protected Optional<ManualClock> storeClock() {
        return Optional.of(CLOCK);
    }
}
