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

import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.store.tck.RangeStoreContract;

/**
 * The range contract on a MongoDB replica set; every store instance is a new {@link MongoRangeStore} on the shared client.
 */
class MongoRangeStoreTest extends RangeStoreContract {
    @Override
    protected RangeStore newStore() {
        return new MongoRangeStore(MongoTestSupport.database(), MongoTestSupport.TIMEOUT);
    }
}
