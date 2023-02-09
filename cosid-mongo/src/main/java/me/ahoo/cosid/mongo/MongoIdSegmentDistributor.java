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

import me.ahoo.cosid.segment.IdSegmentDistributor;

import com.google.common.base.Preconditions;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Mongo IdSegment Distributor.
 *
 * @author ahoo wang
 */
@Slf4j
public class MongoIdSegmentDistributor implements IdSegmentDistributor {
    private final String namespace;
    private final String name;
    private final long step;
    private final MongoCollection<Document> cosidCollection;
    
    public MongoIdSegmentDistributor(String namespace, String name, long step, MongoCollection<Document> cosidCollection) {
        this.namespace = namespace;
        this.name = name;
        this.step = step;
        this.cosidCollection = cosidCollection;
    }
    
    @Nonnull
    @Override
    public String getNamespace() {
        return namespace;
    }
    
    @Nonnull
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public long getStep() {
        return step;
    }
    
    @Override
    public long nextMaxId(long step) {
        String namespacedName = getNamespacedName();
        Document afterDoc = cosidCollection.findOneAndUpdate(
            Filters.eq(Documents.ID_FIELD, namespacedName),
            Updates.combine(
                Updates.inc(Documents.LAST_MAX_ID_FIELD, step),
                Updates.set(Documents.LAST_FETCH_TIME_FIELD, System.currentTimeMillis())
            ),
            Documents.INC_OPTIONS);
        
        assert afterDoc != null;
        Preconditions.checkNotNull(afterDoc, "IdSegment[%s] can not be null!", namespacedName);
        Long lastMaxId = afterDoc.getLong(Documents.LAST_MAX_ID_FIELD);
        return Objects.requireNonNull(lastMaxId);
    }
}
