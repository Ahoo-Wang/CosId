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

package me.ahoo.cosid.segment.grouped;

import me.ahoo.cosid.segment.IdSegment;
import me.ahoo.cosid.segment.IdSegmentChain;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;

import javax.annotation.Nonnull;

public class DefaultGroupedIdSegmentDistributor implements GroupedIdSegmentDistributor {
    private final GroupBySupplier groupBySupplier;
    private final IdSegmentDistributorDefinition idSegmentDistributorDefinition;
    private final IdSegmentDistributorFactory idSegmentDistributorFactory;
    private volatile GroupedBinding currentGroup;
    
    public DefaultGroupedIdSegmentDistributor(GroupBySupplier groupBySupplier, IdSegmentDistributorDefinition idSegmentDistributorDefinition, IdSegmentDistributorFactory idSegmentDistributorFactory) {
        this.groupBySupplier = groupBySupplier;
        this.idSegmentDistributorDefinition = idSegmentDistributorDefinition;
        this.idSegmentDistributorFactory = idSegmentDistributorFactory;
        this.ensureGrouped();
    }
    
    private GroupedBinding ensureGrouped() {
        GroupedKey groupedKey = groupBySupplier.get();
        if (currentGroup != null && currentGroup.group().equals(groupedKey)) {
            return currentGroup;
        }
        synchronized (this) {
            if (currentGroup != null && currentGroup.group().equals(groupedKey)) {
                return currentGroup;
            }
            String groupedName = idSegmentDistributorDefinition.getName() + "@" + groupedKey.getKey();
            IdSegmentDistributorDefinition groupedDef = new IdSegmentDistributorDefinition(idSegmentDistributorDefinition.getNamespace(),
                groupedName,
                idSegmentDistributorDefinition.getOffset(),
                idSegmentDistributorDefinition.getStep());
            this.currentGroup = new GroupedBinding(groupedKey, idSegmentDistributorFactory.create(groupedDef));
        }
        
        return currentGroup;
    }
    
    @Override
    public GroupBySupplier groupBySupplier() {
        return groupBySupplier;
    }
    
    @Nonnull
    @Override
    public String getNamespace() {
        return this.ensureGrouped().getNamespace();
    }
    
    @Nonnull
    @Override
    public String getName() {
        return this.ensureGrouped().getName();
    }
    
    @Override
    public long getStep() {
        return this.ensureGrouped().getStep();
    }
    
    @Override
    public long nextMaxId(long step) {
        return this.ensureGrouped().nextMaxId(step);
    }
    
    private long getMinTtl(long ttl) {
        long groupedTtl = currentGroup.group().ttl();
        return Math.min(groupedTtl, ttl);
    }
    
    @Nonnull
    @Override
    public IdSegment nextIdSegment(long ttl) {
        long minTtl = getMinTtl(ttl);
        return this.ensureGrouped().nextIdSegment(minTtl);
    }
    
    @Nonnull
    @Override
    public IdSegment nextIdSegment(int segments, long ttl) {
        long minTtl = getMinTtl(ttl);
        return this.ensureGrouped().nextIdSegment(segments, minTtl);
    }
    
    @Nonnull
    @Override
    public IdSegmentChain nextIdSegmentChain(IdSegmentChain previousChain, int segments, long ttl) {
        long minTtl = getMinTtl(ttl);
        return this.ensureGrouped().nextIdSegmentChain(previousChain, segments, minTtl);
    }
    
    public static class GroupedBinding implements IdSegmentDistributor {
        
        private final GroupedKey group;
        private final IdSegmentDistributor idSegmentDistributor;
        
        public GroupedBinding(GroupedKey group, IdSegmentDistributor idSegmentDistributor) {
            this.group = group;
            this.idSegmentDistributor = idSegmentDistributor;
        }
        
        public GroupedKey group() {
            return group;
        }
        
        @Nonnull
        @Override
        public String getNamespace() {
            return idSegmentDistributor.getNamespace();
        }
        
        @Nonnull
        @Override
        public String getName() {
            return idSegmentDistributor.getName();
        }
        
        @Override
        public long getStep() {
            return idSegmentDistributor.getStep();
        }
        
        @Override
        public long nextMaxId(long step) {
            return idSegmentDistributor.nextMaxId(step);
        }
        
    }
}
