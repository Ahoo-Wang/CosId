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

import com.google.common.base.Preconditions;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

import java.util.Objects;

@Slf4j
public class MongoCosIdSegmentCollection implements CosIdSegmentCollection {
    private final MongoCollection<Document> cosidCollection;
    
    public MongoCosIdSegmentCollection(MongoCollection<Document> cosidCollection) {
        this.cosidCollection = cosidCollection;
    }
    
    @Override
    public long incrementAndGet(String namespacedName, long step) {
        Document afterDoc = cosidCollection.findOneAndUpdate(
            Filters.eq(Documents.ID_FIELD, namespacedName),
            Updates.combine(
                Updates.inc(Documents.LAST_MAX_ID_FIELD, step),
                Updates.set(Documents.LAST_FETCH_TIME_FIELD, System.currentTimeMillis())
            ),
            Documents.UPDATE_AFTER_OPTIONS);
        
        assert afterDoc != null;
        Preconditions.checkNotNull(afterDoc, "IdSegment[%s] can not be null!", namespacedName);
        Long lastMaxId = afterDoc.getLong(Documents.LAST_MAX_ID_FIELD);
        return Objects.requireNonNull(lastMaxId);
    }
    
    @Override
    public boolean ensureIdSegment(String segmentName, long offset) {
        if (log.isInfoEnabled()) {
            log.info("Ensure IdSegment:[{}]", segmentName);
        }
        try {
            cosidCollection.insertOne(new Document()
                .append(Documents.ID_FIELD, segmentName)
                .append(Documents.LAST_MAX_ID_FIELD, offset)
                .append(Documents.LAST_FETCH_TIME_FIELD, 0L)
            );
            return true;
        } catch (MongoWriteException mongoWriteException) {
            if (log.isInfoEnabled()) {
                log.info("Ensure IdSegment:[{}] Failed", segmentName, mongoWriteException);
            }
            return false;
        }
    }
}
