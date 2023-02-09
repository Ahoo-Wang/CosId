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

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;

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
    private final CosIdSegmentCollection cosIdSegmentCollection;
    
    public MongoIdSegmentDistributor(String namespace, String name, long step, CosIdSegmentCollection cosIdSegmentCollection) {
        this.namespace = namespace;
        this.name = name;
        this.step = step;
        this.cosIdSegmentCollection = cosIdSegmentCollection;
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
        return cosIdSegmentCollection.incrementAndGet(namespacedName, step);
    }
}
