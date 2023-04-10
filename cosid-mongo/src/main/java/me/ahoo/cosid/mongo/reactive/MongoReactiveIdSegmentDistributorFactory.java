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

package me.ahoo.cosid.mongo.reactive;

import static me.ahoo.cosid.mongo.IdSegmentCollection.COLLECTION_NAME;

import me.ahoo.cosid.mongo.IdSegmentCollection;
import me.ahoo.cosid.mongo.MongoIdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;

import com.mongodb.reactivestreams.client.MongoDatabase;

public class MongoReactiveIdSegmentDistributorFactory implements IdSegmentDistributorFactory {
    private final MongoDatabase mongoDatabase;
    private final boolean enableAutoInitIdSegment;
    
    public MongoReactiveIdSegmentDistributorFactory(MongoDatabase mongoDatabase, boolean enableAutoInitIdSegment) {
        this.mongoDatabase = mongoDatabase;
        this.enableAutoInitIdSegment = enableAutoInitIdSegment;
    }
    
    @Override
    public IdSegmentDistributor create(IdSegmentDistributorDefinition definition) {
        IdSegmentCollection idSegmentCollection = new MongoReactiveIdSegmentCollection(mongoDatabase.getCollection(COLLECTION_NAME));
        if (enableAutoInitIdSegment) {
            idSegmentCollection.ensureIdSegment(definition.getNamespacedName(), definition.getOffset());
        }
        
        return new MongoIdSegmentDistributor(definition.getNamespace(),
            definition.getName(),
            definition.getStep(),
            idSegmentCollection);
    }
}
