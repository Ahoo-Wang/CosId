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

import static me.ahoo.cosid.mongo.IdSegmentOperates.incrementAndGetUpdates;

import com.google.common.base.Preconditions;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

import java.util.Objects;

@Slf4j
public class MongoIdSegmentCollection implements IdSegmentCollection {
    private final MongoCollection<Document> cosidCollection;
    
    public MongoIdSegmentCollection(MongoCollection<Document> cosidCollection) {
        this.cosidCollection = cosidCollection;
    }
    
    @Override
    public long incrementAndGet(String namespacedName, long step) {
        Document afterDoc = cosidCollection.findOneAndUpdate(
            Filters.eq(Documents.ID_FIELD, namespacedName),
            incrementAndGetUpdates(step),
            Documents.UPDATE_UPSERT_AFTER_OPTIONS);
        
        assert afterDoc != null;
        Preconditions.checkNotNull(afterDoc, "IdSegment[%s] can not be null!", namespacedName);
        Long lastMaxId = afterDoc.getLong(IdSegmentOperates.LAST_MAX_ID_FIELD);
        return Objects.requireNonNull(lastMaxId);
    }
}
