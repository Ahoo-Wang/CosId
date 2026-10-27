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

package me.ahoo.cosid.store.mongo;

import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.store.tck.SlotLeaseStoreContract;

/**
 * The slot lease contract on a MongoDB replica set; every store instance is a new {@link MongoSlotLeaseStore} on the
 * shared client, with the server clock ($$NOW) as the store clock.
 */
class MongoSlotLeaseStoreTest extends SlotLeaseStoreContract {
    @Override
    protected SlotLeaseStore newStore() {
        return new MongoSlotLeaseStore(MongoTestSupport.database(), MongoTestSupport.TIMEOUT);
    }
}
