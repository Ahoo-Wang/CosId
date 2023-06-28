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

package me.ahoo.cosid.mongo;

import me.ahoo.cosid.mongo.reactive.MongoReactiveIdSegmentDistributorFactory;
import me.ahoo.cosid.mongo.reactive.MongoReactiveIdSegmentInitializer;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.test.segment.distributor.GroupedIdSegmentDistributorSpec;
import me.ahoo.cosid.test.segment.distributor.IdSegmentDistributorSpec;

import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import org.junit.jupiter.api.BeforeEach;

class GroupedMongoReactiveIdSegmentDistributorTest extends GroupedIdSegmentDistributorSpec {
    MongoDatabase mongoDatabase;
    IdSegmentDistributorFactory distributorFactory;
    MongoReactiveIdSegmentInitializer idSegmentInitializer;
    
    @BeforeEach
    void setup() {
        mongoDatabase = MongoClients.create(MongoLauncher.getConnectionString()).getDatabase("cosid_db");
        idSegmentInitializer = new MongoReactiveIdSegmentInitializer(mongoDatabase);
        idSegmentInitializer.ensureCosIdCollection();
        distributorFactory =
            new MongoReactiveIdSegmentDistributorFactory(mongoDatabase, true);
    }
    
    @Override
    protected IdSegmentDistributorFactory getFactory() {
        return distributorFactory;
    }
}