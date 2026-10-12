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

import static me.ahoo.cosid.machine.MachineIdDistributor.namespacedMachineId;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InMemoryMachineStateStorage;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.test.container.MongoLauncher;
import me.ahoo.cosid.test.machine.lease.MixedLeaseFormatSpec;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;
import java.util.UUID;

/**
 * Runs {@link MixedLeaseFormatSpec} on a real MongoDB: {@link MongoMachineIdDistributor} as the 3.4 instances.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MongoMixedLeaseFormatTest extends MixedLeaseFormatSpec {
    MongoClient mongoClient;
    MongoDatabase mongoDatabase;
    MongoMachineLeaseStore store;
    
    @BeforeAll
    void setup() {
        mongoClient = MongoClients.create(MongoLauncher.getConnectionString());
        mongoDatabase = mongoClient.getDatabase("cosid_db_mixed_" + UUID.randomUUID().toString().replace("-", ""));
        new MongoMachineInitializer(mongoDatabase).ensureMachineCollection();
        store = new MongoMachineLeaseStore(mongoDatabase, Duration.ofSeconds(5));
    }
    
    @AfterAll
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
    protected MachineIdDistributor v34() {
        return new MongoMachineIdDistributor(new MongoMachineCollection(mongoDatabase.getCollection(MachineCollection.COLLECTION_NAME)),
            new InMemoryMachineStateStorage(), ClockBackwardsSynchronizer.DEFAULT);
    }
    
    @Override
    protected MachineIdDistributor v40() {
        return new LeaseStoreMachineIdDistributor(store, new InMemoryMachineStateStorage(), ClockBackwardsSynchronizer.DEFAULT);
    }
    
    @Override
    protected void takeReleasedAs34WithTheSameTimestamp(String namespace, int machineId, String instanceId) {
        long matched = mongoDatabase.getCollection(MachineCollection.COLLECTION_NAME).updateOne(
            Filters.and(Filters.eq(Documents.ID_FIELD, namespacedMachineId(namespace, machineId)), Filters.eq(MachineOperates.INSTANCE_ID_FIELD, "")),
            Updates.set(MachineOperates.INSTANCE_ID_FIELD, instanceId)).getMatchedCount();
        assertThat(matched, equalTo(1L));
    }
}
