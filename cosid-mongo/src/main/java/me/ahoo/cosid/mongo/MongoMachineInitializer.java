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

import static me.ahoo.cosid.mongo.MachineOperates.COLLECTION_NAME;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

@Slf4j
public class MongoMachineInitializer implements MachineInitializer {
    static final String NAMESPACE_MACHINE_ID_INDEX = "namespace_machineId";
    static final String NAMESPACE_INSTANCE_ID_INDEX = "namespace_instanceId";
    private static final int NAMESPACE_EXISTS = 48;
    private final MongoDatabase mongoDatabase;
    
    public MongoMachineInitializer(MongoDatabase mongoDatabase) {
        this.mongoDatabase = mongoDatabase;
    }
    
    /**
     * Create the collection when missing and its indexes, idempotently: also on a collection created by an older
     * version. Its hashed {@code machineId} and {@code instanceId} indexes are kept, since older nodes create them
     * again: the lease queries no longer use them, and an operator can drop them once every node runs 4.1.
     */
    @Override
    public boolean ensureMachineCollection() {
        if (log.isInfoEnabled()) {
            log.info("Ensure MachineCollection");
        }
        try {
            createCollectionIfMissing();
            MongoCollection<Document> machineCollection = mongoDatabase.getCollection(COLLECTION_NAME);
            machineCollection.createIndex(Indexes.ascending(MachineOperates.NAMESPACE_FIELD, MachineOperates.MACHINE_ID_FIELD),
                new IndexOptions().name(NAMESPACE_MACHINE_ID_INDEX));
            machineCollection.createIndex(Indexes.ascending(MachineOperates.NAMESPACE_FIELD, MachineOperates.INSTANCE_ID_FIELD),
                new IndexOptions().name(NAMESPACE_INSTANCE_ID_INDEX));
            return true;
        } catch (MongoCommandException mongoCommandException) {
            if (log.isWarnEnabled()) {
                log.warn("Ensure MachineCollection Failed", mongoCommandException);
            }
            return false;
        }
    }
    
    private void createCollectionIfMissing() {
        try {
            mongoDatabase.createCollection(COLLECTION_NAME);
        } catch (MongoCommandException mongoCommandException) {
            if (mongoCommandException.getErrorCode() != NAMESPACE_EXISTS) {
                throw mongoCommandException;
            }
        }
    }
}
