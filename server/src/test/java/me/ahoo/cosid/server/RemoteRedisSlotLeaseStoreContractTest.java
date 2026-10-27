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
import me.ahoo.cosid.store.tck.SlotLeaseStoreContract;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * The contract through store/remote against a cosid-server on the Redis store at {@code COSID_TEST_REDIS_URI} (the
 * CI job "Server" provides one); skipped without it, the H2 and in-memory runs cover the contract then.
 */
@EnabledIfEnvironmentVariable(named = "COSID_TEST_REDIS_URI", matches = ".+")
class RemoteRedisSlotLeaseStoreContractTest extends SlotLeaseStoreContract {
    private static TestServer server;

    @BeforeAll
    static void start() {
        server = TestServer.start(null, "cosid.stores.main.type=redis", "cosid.stores.main.options.uri=" + System.getenv("COSID_TEST_REDIS_URI"),
            TestServer.CONTRACT_LIMITS[0], TestServer.CONTRACT_LIMITS[1]);
    }

    @AfterAll
    static void stop() {
        server.close();
    }

    @Override
    protected SlotLeaseStore newStore() {
        return RemoteContractSupport.slots(server);
    }
}
