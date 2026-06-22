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
import me.ahoo.cosid.mongo.reactive.BlockingAdapter;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.test.container.MongoLauncher;
import me.ahoo.cosid.test.segment.distributor.GroupedIdSegmentDistributorSpec;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.UUID;

class GroupedMongoReactiveIdSegmentDistributorTest extends GroupedIdSegmentDistributorSpec {
    MongoClient mongoClient;
    MongoDatabase mongoDatabase;
    IdSegmentDistributorFactory distributorFactory;
    MongoReactiveIdSegmentInitializer idSegmentInitializer;
    
    @BeforeEach
    void setup() {
        mongoClient = MongoClients.create(MongoLauncher.getConnectionString());
        mongoDatabase = mongoClient.getDatabase("cosid_db_grouped_reactive_" + UUID.randomUUID().toString().replace("-", ""));
        idSegmentInitializer = new MongoReactiveIdSegmentInitializer(mongoDatabase);
        idSegmentInitializer.ensureCosIdCollection();
        distributorFactory =
            new MongoReactiveIdSegmentDistributorFactory(mongoDatabase);
    }

    @AfterEach
    void destroy() {
        try {
            if (mongoDatabase != null) {
                BlockingAdapter.block(mongoDatabase.drop());
            }
        } finally {
            if (mongoClient != null) {
                mongoClient.close();
            }
        }
    }
    
    @Override
    protected IdSegmentDistributorFactory getFactory() {
        return distributorFactory;
    }
}
