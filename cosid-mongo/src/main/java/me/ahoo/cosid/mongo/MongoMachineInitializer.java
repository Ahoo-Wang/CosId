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

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class MongoMachineInitializer implements MachineInitializer {
    static final String NAMESPACE_MACHINE_ID_INDEX = "namespace_machineId";
    static final String NAMESPACE_INSTANCE_ID_INDEX = "namespace_instanceId";
    private static final int NAMESPACE_EXISTS = 48;
    /**
     * An index with the same keys exists under another name or with other options.
     */
    private static final int INDEX_OPTIONS_CONFLICT = 85;
    private final MongoDatabase mongoDatabase;
    
    public MongoMachineInitializer(MongoDatabase mongoDatabase) {
        this.mongoDatabase = mongoDatabase;
    }
    
    /**
     * Create the collection when missing and its indexes, idempotently: also on a collection created by an older
     * version. Its hashed {@code machineId} and {@code instanceId} indexes are kept, since older nodes create them
     * again: the lease queries no longer use them, and an operator can drop them once every node runs 4.1.
     *
     * <p>Each index is created on its own: one that already exists under another name or with other options (an index
     * managed by a DBA) is logged at INFO and does not stop the other. Any other failure is logged once, at WARN.</p>
     *
     * @return true if the collection and both indexes are in place
     */
    @Override
    public boolean ensureMachineCollection() {
        if (log.isInfoEnabled()) {
            log.info("Ensure MachineCollection");
        }
        try {
            createCollectionIfMissing();
        } catch (MongoCommandException mongoCommandException) {
            if (log.isWarnEnabled()) {
                log.warn("Ensure MachineCollection Failed", mongoCommandException);
            }
            return false;
        }
        MongoCollection<Document> machineCollection = mongoDatabase.getCollection(COLLECTION_NAME);
        List<String> failed = new ArrayList<>();
        MongoCommandException firstFailure = null;
        for (String[] index : new String[][] {
            {NAMESPACE_MACHINE_ID_INDEX, MachineOperates.MACHINE_ID_FIELD},
            {NAMESPACE_INSTANCE_ID_INDEX, MachineOperates.INSTANCE_ID_FIELD}}) {
            try {
                machineCollection.createIndex(Indexes.ascending(MachineOperates.NAMESPACE_FIELD, index[1]), new IndexOptions().name(index[0]));
            } catch (MongoCommandException mongoCommandException) {
                if (mongoCommandException.getErrorCode() == INDEX_OPTIONS_CONFLICT) {
                    if (log.isInfoEnabled()) {
                        log.info("Index [{}] of {} already exists under another name or with other options: kept as it is. {}", index[0],
                            COLLECTION_NAME, mongoCommandException.getErrorMessage());
                    }
                    continue;
                }
                failed.add(index[0]);
                if (firstFailure == null) {
                    firstFailure = mongoCommandException;
                }
            }
        }
        if (firstFailure != null) {
            if (log.isWarnEnabled()) {
                log.warn("Ensure MachineCollection Failed: indexes {} not created; the lease queries run without them.", failed, firstFailure);
            }
            return false;
        }
        return true;
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
