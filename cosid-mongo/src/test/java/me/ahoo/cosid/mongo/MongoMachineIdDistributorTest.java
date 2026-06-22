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

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InMemoryMachineStateStorage;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.test.Assert;
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.test.container.MongoLauncher;
import me.ahoo.cosid.test.machine.distributor.MachineIdDistributorSpec;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class MongoMachineIdDistributorTest extends MachineIdDistributorSpec {
    MongoClient mongoClient;
    MongoDatabase mongoDatabase;
    MachineIdDistributor machineIdDistributor;
    MongoMachineInitializer machineInitializer;
    MachineStateStorage machineStateStorage;
    
    @BeforeEach
    void setup() {
        mongoClient = MongoClients.create(MongoLauncher.getConnectionString());
        mongoDatabase = mongoClient.getDatabase("cosid_db_machine_" + UUID.randomUUID().toString().replace("-", ""));
        machineStateStorage = new InMemoryMachineStateStorage();
        machineInitializer = new MongoMachineInitializer(mongoDatabase);
        machineInitializer.ensureMachineCollection();
        machineIdDistributor = new MongoMachineIdDistributor(
            new MongoMachineCollection(mongoDatabase.getCollection(MachineCollection.COLLECTION_NAME)),
            machineStateStorage,
            ClockBackwardsSynchronizer.DEFAULT);
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
    
    @Override
    protected MachineIdDistributor getDistributor() {
        return machineIdDistributor;
    }

    @Test
    @Override
    public void guardLost() {
        MachineIdDistributor distributor = getDistributor();
        String namespace = MockIdGenerator.usePrefix("GuardLost").generateAsString();
        InstanceId instanceId = mockInstance(0, false);
        machineStateStorage.set(namespace, getMachineBit(), instanceId);

        Assert.assertThrows(MachineIdLostException.class, () -> {
            distributor.guard(namespace, instanceId, MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION);
        });
    }

}
