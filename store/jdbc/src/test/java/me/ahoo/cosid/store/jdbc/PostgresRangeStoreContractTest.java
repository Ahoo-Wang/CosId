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

package me.ahoo.cosid.store.jdbc;

import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.store.tck.RangeStoreContract;

import org.junit.jupiter.api.BeforeAll;

/**
 * The range contract on POSTGRES ({@link TestDatabase#POSTGRES}); each store instance shares the pool of the database, so
 * reserves of different keys run on the same pooled connections.
 */
class PostgresRangeStoreContractTest extends RangeStoreContract {
    @BeforeAll
    static void database() {
        TestDatabase.POSTGRES.assumeAvailable();
    }

    @Override
    protected RangeStore newStore() {
        return TestDatabase.POSTGRES.store().ranges();
    }
}
