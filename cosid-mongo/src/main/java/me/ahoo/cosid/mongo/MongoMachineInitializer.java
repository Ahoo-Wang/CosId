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

import static me.ahoo.cosid.mongo.MachineCollection.COLLECTION_NAME;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

@Slf4j
public class MongoMachineInitializer implements MachineInitializer {
    private final MongoDatabase mongoDatabase;
    
    public MongoMachineInitializer(MongoDatabase mongoDatabase) {
        this.mongoDatabase = mongoDatabase;
    }
    
    @Override
    public boolean ensureMachineCollection() {
        if (log.isInfoEnabled()) {
            log.info("Ensure MachineCollection");
        }
        try {
            mongoDatabase.createCollection(COLLECTION_NAME);
            MongoCollection<Document> machineCollection = mongoDatabase.getCollection(COLLECTION_NAME);
            machineCollection.createIndex(Indexes.hashed(MachineOperates.MACHINE_ID_FIELD));
            machineCollection.createIndex(Indexes.hashed(MachineOperates.INSTANCE_ID_FIELD));
            return true;
        } catch (MongoCommandException mongoCommandException) {
            if (log.isInfoEnabled()) {
                log.info("Ensure MachineCollection Failed", mongoCommandException);
            }
            return false;
        }
    }
    
}
