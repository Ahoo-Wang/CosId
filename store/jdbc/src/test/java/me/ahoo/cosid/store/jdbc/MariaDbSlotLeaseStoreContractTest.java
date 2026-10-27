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

import static org.junit.jupiter.api.Assertions.assertEquals;

import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.store.tck.SlotLeaseStoreContract;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The slot lease contract on MARIADB ({@link TestDatabase#MARIADB}), on the store clock of the database.
 */
class MariaDbSlotLeaseStoreContractTest extends SlotLeaseStoreContract {
    @BeforeAll
    static void database() {
        TestDatabase.MARIADB.assumeAvailable();
    }

    @Override
    protected SlotLeaseStore newStore() {
        return TestDatabase.MARIADB.store().slots();
    }

    @Test
    void detectsTheDialectOfTheDatabase() {
        assertEquals(TestDatabase.MARIADB.dialect(), JdbcStore.of(TestDatabase.MARIADB.dataSource(), TestDatabase.TIMEOUT).dialect());
    }
}
