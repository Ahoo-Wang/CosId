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
import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

import java.util.Objects;

@Slf4j
public class MongoIdSegmentCollection implements IdSegmentCollection {
    private static final int DUPLICATE_KEY = 11000;
    private final MongoCollection<Document> cosidCollection;

    public MongoIdSegmentCollection(MongoCollection<Document> cosidCollection) {
        this.cosidCollection = cosidCollection;
    }

    /**
     * One {@code findOneAndUpdate} with upsert: creates the segment at {@code offset} when missing, adds {@code step} and
     * stamps the server's time.
     */
    @Override
    public long incrementAndGet(String namespacedName, long offset, long step) {
        Document afterDoc;
        try {
            afterDoc = fetch(namespacedName, offset, step);
        } catch (MongoCommandException mongoCommandException) {
            // Two first fetches at once: the other upsert created the document. The server retries this itself since
            // 4.2; once more here covers the rest.
            if (mongoCommandException.getErrorCode() != DUPLICATE_KEY) {
                throw mongoCommandException;
            }
            afterDoc = fetch(namespacedName, offset, step);
        }

        Preconditions.checkNotNull(afterDoc, "IdSegment[%s] can not be null!", namespacedName);
        Long lastMaxId = afterDoc.getLong(IdSegmentOperates.LAST_MAX_ID_FIELD);
        return Objects.requireNonNull(lastMaxId);
    }

    private Document fetch(String namespacedName, long offset, long step) {
        return cosidCollection.findOneAndUpdate(
            Filters.eq(Documents.ID_FIELD, namespacedName),
            IdSegmentOperates.fetchUpdates(offset, step),
            Documents.UPDATE_UPSERT_AFTER_OPTIONS);
    }
}
