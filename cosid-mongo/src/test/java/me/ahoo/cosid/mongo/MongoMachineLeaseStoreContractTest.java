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

import me.ahoo.cosid.machine.lease.MachineLeaseStore;
import me.ahoo.cosid.test.container.MongoLauncher;
import me.ahoo.cosid.test.machine.lease.MachineLeaseStoreSpec;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;
import java.util.UUID;

/**
 * Runs {@link MachineLeaseStoreSpec} against a real MongoDB.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MongoMachineLeaseStoreContractTest extends MachineLeaseStoreSpec {
    MongoClient mongoClient;
    MongoDatabase mongoDatabase;
    MongoMachineLeaseStore store;
    
    @BeforeAll
    void setup() {
        mongoClient = MongoClients.create(MongoLauncher.getConnectionString());
        mongoDatabase = mongoClient.getDatabase("cosid_db_lease_" + UUID.randomUUID().toString().replace("-", ""));
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
    protected MachineLeaseStore getStore() {
        return store;
    }
    
    @Override
    protected boolean separatesWatermark() {
        // Still the 3.4 format: one last timestamp for the watermark and the renewal.
        return false;
    }
}
