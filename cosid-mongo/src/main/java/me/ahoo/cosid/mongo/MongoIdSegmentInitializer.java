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

import com.mongodb.MongoCommandException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

@Slf4j
public class MongoIdSegmentInitializer {
    private final MongoDatabase mongoDatabase;
    
    public MongoIdSegmentInitializer(MongoDatabase mongoDatabase) {
        this.mongoDatabase = mongoDatabase;
    }
    
    public boolean tryInitCosIdCollection() {
        if (log.isInfoEnabled()) {
            log.info("Try Init CosIdCollection");
        }
        try {
            mongoDatabase.createCollection(MongoIdSegmentDistributorFactory.COSID_COLLECTION_NAME);
            return true;
        } catch (MongoCommandException mongoCommandException) {
            if (log.isInfoEnabled()) {
                log.info("Init CosIdCollection Failed", mongoCommandException);
            }
            return false;
        }
    }
    
    public boolean tryInitIdSegment(String segmentName, long offset) {
        if (log.isInfoEnabled()) {
            log.info("Try Init IdSegment:[{}]", segmentName);
        }
        try {
            MongoCollection<Document> cosidCollection = mongoDatabase.getCollection(MongoIdSegmentDistributorFactory.COSID_COLLECTION_NAME);
            cosidCollection.insertOne(new Document()
                .append(Documents.ID_FIELD, segmentName)
                .append(Documents.LAST_MAX_ID_FIELD, offset)
                .append(Documents.LAST_FETCH_TIME_FIELD, 0L)
            );
            return true;
        } catch (MongoWriteException mongoWriteException) {
            if (log.isInfoEnabled()) {
                log.info("Init IdSegment:[{}] Failed", segmentName, mongoWriteException);
            }
            return false;
        }
        
    }
}
