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

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;
import static me.ahoo.cosid.mongo.IdSegmentCollection.COLLECTION_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.test.container.MongoLauncher;
import me.ahoo.cosid.test.segment.distributor.IdSegmentDistributorSpec;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class MongoIdSegmentDistributorTest extends IdSegmentDistributorSpec {
    MongoClient mongoClient;
    MongoDatabase mongoDatabase;
    IdSegmentDistributorFactory distributorFactory;
    MongoIdSegmentInitializer idSegmentInitializer;
    
    @BeforeEach
    void setup() {
        mongoClient = MongoClients.create(MongoLauncher.getConnectionString());
        mongoDatabase = mongoClient.getDatabase("cosid_db_" + UUID.randomUUID().toString().replace("-", ""));
        idSegmentInitializer = new MongoIdSegmentInitializer(mongoDatabase);
        idSegmentInitializer.ensureCosIdCollection();
        distributorFactory =
            new MongoIdSegmentDistributorFactory(mongoDatabase);
    }

    @AfterEach
    void destroy() {
        try {
            if (mongoDatabase != null) {
                mongoDatabase.drop();
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
    
    @Override
    protected <T extends IdSegmentDistributor> void setMaxIdBack(T distributor, long maxId) {
        mongoDatabase.getCollection(COLLECTION_NAME)
            .updateOne(eq(Documents.ID_FIELD, distributor.getNamespacedName()), set(IdSegmentOperates.LAST_MAX_ID_FIELD, maxId));
    }
    
    @Test
    @Override
    public void nextMaxIdWhenBack() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "nextMaxIdWhenBack", TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = factory().create(definition);
        long firstMaxId = distributor.nextMaxId();
        assertThat(firstMaxId, equalTo(TEST_OFFSET + TEST_STEP));

        setMaxIdBack(distributor, TEST_OFFSET);
        long nextMaxId = distributor.nextMaxId();

        assertThat(nextMaxId, equalTo(TEST_OFFSET + TEST_STEP));
    }
}
