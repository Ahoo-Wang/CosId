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

import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.store.tck.InMemoryStore;
import me.ahoo.cosid.store.tck.RangeStoreContract;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/**
 * The range contract through store/remote against a real cosid-server, started in process on {@link InMemoryStore}.
 */
class RemoteRangeStoreContractTest extends RangeStoreContract {
    private static TestServer server;

    @BeforeAll
    static void start() {
        server = TestServer.memory(new InMemoryStore(), TestServer.CONTRACT_LIMITS);
    }

    @AfterAll
    static void stop() {
        server.close();
    }

    @Override
    protected RangeStore newStore() {
        return RemoteContractSupport.ranges(server);
    }
}
