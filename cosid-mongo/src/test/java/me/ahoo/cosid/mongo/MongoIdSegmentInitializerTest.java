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

import me.ahoo.cosid.test.container.MongoLauncher;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class MongoIdSegmentInitializerTest {
    MongoClient mongoClient;
    MongoDatabase mongoDatabase;
    MongoIdSegmentInitializer idSegmentInitializer;
    MongoIdSegmentCollection cosIdSegmentCollection;
    
    @BeforeEach
    void setup() {
        mongoClient = MongoClients.create(MongoLauncher.getConnectionString());
        mongoDatabase = mongoClient.getDatabase("cosid_db_initializer_" + UUID.randomUUID().toString().replace("-", ""));
        idSegmentInitializer = new MongoIdSegmentInitializer(mongoDatabase);
        cosIdSegmentCollection = new MongoIdSegmentCollection(mongoDatabase.getCollection(IdSegmentCollection.COLLECTION_NAME));
    }

    @AfterEach
    void destroy() {
        if (mongoDatabase != null) {
            mongoDatabase.drop();
        }
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
    
    @Test
    void ensureCosIdCollection() {
        Assertions.assertTrue(idSegmentInitializer.ensureCosIdCollection());
        idSegmentInitializer.ensureCosIdCollection();
        Assertions.assertTrue(mongoDatabase.listCollectionNames()
            .into(new java.util.ArrayList<>())
            .contains(IdSegmentCollection.COLLECTION_NAME));
    }
}
