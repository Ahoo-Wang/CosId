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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import me.ahoo.cosid.test.container.MongoLauncher;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The machine collection gets compound indexes on the namespace, also when created by an older version, on a real
 * MongoDB.
 */
class MongoMachineInitializerContractTest {
    MongoClient mongoClient;
    MongoDatabase mongoDatabase;
    
    @BeforeEach
    void setup() {
        mongoClient = MongoClients.create(MongoLauncher.getConnectionString());
        mongoDatabase = mongoClient.getDatabase("cosid_db_init_" + UUID.randomUUID().toString().replace("-", ""));
    }
    
    @AfterEach
    void destroy() {
        mongoDatabase.drop();
        mongoClient.close();
    }
    
    private List<String> indexNames() {
        List<String> names = new ArrayList<>();
        mongoDatabase.getCollection(MachineOperates.COLLECTION_NAME).listIndexes().forEach(index -> names.add(index.getString("name")));
        return names;
    }
    
    @Test
    void createsTheCompoundIndexesIdempotently() {
        MongoMachineInitializer initializer = new MongoMachineInitializer(mongoDatabase);
        assertThat(initializer.ensureMachineCollection(), is(true));
        assertThat(initializer.ensureMachineCollection(), is(true));
        assertThat(indexNames(), hasItems(MongoMachineInitializer.NAMESPACE_MACHINE_ID_INDEX, MongoMachineInitializer.NAMESPACE_INSTANCE_ID_INDEX));
    }
    
    @Test
    void addsTheCompoundIndexesToAnOlderCollectionAndKeepsItsHashedOnes() {
        // As 3.x and 4.0 created it.
        mongoDatabase.createCollection(MachineOperates.COLLECTION_NAME);
        MongoCollection<Document> collection = mongoDatabase.getCollection(MachineOperates.COLLECTION_NAME);
        collection.createIndex(Indexes.hashed(MachineOperates.MACHINE_ID_FIELD));
        collection.createIndex(Indexes.hashed(MachineOperates.INSTANCE_ID_FIELD));
        
        assertThat(new MongoMachineInitializer(mongoDatabase).ensureMachineCollection(), is(true));
        
        // Older nodes would create the hashed indexes again: they stay, for an operator to drop once all nodes run 4.1.
        assertThat(indexNames(), hasItems(MongoMachineInitializer.NAMESPACE_MACHINE_ID_INDEX, MongoMachineInitializer.NAMESPACE_INSTANCE_ID_INDEX,
            "machineId_hashed", "instanceId_hashed"));
    }
    
    @Test
    void anIndexAlreadyThereUnderAnotherNameDoesNotStopTheOther() {
        // A DBA created the machine id index under their own name.
        mongoDatabase.createCollection(MachineOperates.COLLECTION_NAME);
        mongoDatabase.getCollection(MachineOperates.COLLECTION_NAME).createIndex(
            Indexes.ascending(MachineOperates.NAMESPACE_FIELD, MachineOperates.MACHINE_ID_FIELD), new IndexOptions().name("dba_namespace_machine"));
        
        assertThat(new MongoMachineInitializer(mongoDatabase).ensureMachineCollection(), is(true));
        
        assertThat(indexNames(), hasItems("dba_namespace_machine", MongoMachineInitializer.NAMESPACE_INSTANCE_ID_INDEX));
        assertThat(indexNames(), not(hasItem(MongoMachineInitializer.NAMESPACE_MACHINE_ID_INDEX)));
    }
    
    /**
     * The plan the server chose, without the rejected ones: queryPlanner.winningPlan, whose queryPlan on servers with the
     * slot-based engine.
     */
    private static String winningPlan(Document explain) {
        Document winningPlan = explain.get("queryPlanner", Document.class).get("winningPlan", Document.class);
        Document queryPlan = winningPlan.get("queryPlan", Document.class);
        return (queryPlan != null ? queryPlan : winningPlan).toJson();
    }
    
    @Test
    void theLeaseQueriesUseTheIndexes() {
        new MongoMachineInitializer(mongoDatabase).ensureMachineCollection();
        MongoCollection<Document> collection = mongoDatabase.getCollection(MachineOperates.COLLECTION_NAME);
        // Enough machine ids for the planner to tell the indexes apart.
        List<Document> machines = new ArrayList<>();
        for (int machineId = 0; machineId < 200; machineId++) {
            machines.add(new Document(Documents.ID_FIELD, "ns." + machineId).append(MachineOperates.NAMESPACE_FIELD, "ns")
                .append(MachineOperates.MACHINE_ID_FIELD, machineId).append(MachineOperates.INSTANCE_ID_FIELD, machineId < 100 ? "" : "instance-" + machineId));
        }
        collection.insertMany(machines);
        
        Document byMachineId = collection.find(Filters.and(Filters.eq(MachineOperates.NAMESPACE_FIELD, "ns"),
                Filters.eq(MachineOperates.INSTANCE_ID_FIELD, ""), Filters.lte(MachineOperates.MACHINE_ID_FIELD, 7)))
            .sort(Sorts.ascending(MachineOperates.MACHINE_ID_FIELD)).explain();
        assertThat(winningPlan(byMachineId), containsString("\"indexName\": \"" + MongoMachineInitializer.NAMESPACE_MACHINE_ID_INDEX));
        
        Document byInstanceId = collection.find(Filters.and(Filters.eq(MachineOperates.NAMESPACE_FIELD, "ns"),
            Filters.eq(MachineOperates.INSTANCE_ID_FIELD, "instance-150"))).explain();
        assertThat(winningPlan(byInstanceId), containsString("\"indexName\": \"" + MongoMachineInitializer.NAMESPACE_INSTANCE_ID_INDEX));
    }
}
