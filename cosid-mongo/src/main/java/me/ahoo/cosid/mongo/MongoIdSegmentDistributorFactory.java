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
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;

import com.mongodb.client.MongoDatabase;

/**
 * Mongo IdSegment Distributor Factory.
 *
 * @author ahoo wang
 */
public class MongoIdSegmentDistributorFactory implements IdSegmentDistributorFactory {
    private final MongoDatabase mongoDatabase;
    private final MongoIdSegmentInitializer idSegmentInitializer;
    private final boolean enableAutoInitIdSegment;
    public static final String COSID_COLLECTION_NAME = "cosid";
    
    public MongoIdSegmentDistributorFactory(MongoDatabase mongoDatabase, MongoIdSegmentInitializer idSegmentInitializer, boolean enableAutoInitIdSegment) {
        this.mongoDatabase = mongoDatabase;
        this.idSegmentInitializer = idSegmentInitializer;
        this.enableAutoInitIdSegment = enableAutoInitIdSegment;
    }
    
    @Override
    public IdSegmentDistributor create(IdSegmentDistributorDefinition definition) {
        if (enableAutoInitIdSegment) {
            idSegmentInitializer.tryInitIdSegment(definition.getNamespacedName(), definition.getOffset());
        }
        return new MongoIdSegmentDistributor(definition.getNamespace(),
            definition.getName(),
            definition.getStep(),
            mongoDatabase.getCollection(COSID_COLLECTION_NAME));
    }
}
