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

import org.jspecify.annotations.NonNull;

public class DefaultGroupedIdSegmentDistributor implements GroupedIdSegmentDistributor {
    private final GroupBySupplier groupBySupplier;
    private final IdSegmentDistributorDefinition idSegmentDistributorDefinition;
    private final IdSegmentDistributorFactory idSegmentDistributorFactory;
    private volatile GroupedBinding currentGroup;
    
    public DefaultGroupedIdSegmentDistributor(GroupBySupplier groupBySupplier, IdSegmentDistributorDefinition idSegmentDistributorDefinition, IdSegmentDistributorFactory idSegmentDistributorFactory) {
        this.groupBySupplier = groupBySupplier;
        this.idSegmentDistributorDefinition = idSegmentDistributorDefinition;
        this.idSegmentDistributorFactory = idSegmentDistributorFactory;
        this.ensureGroupedBinding();
    }
    
    private GroupedBinding ensureGroupedBinding() {
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
    
    public GroupBySupplier groupBySupplier() {
        return groupBySupplier;
    }
    
    @Override
    public @NonNull String getNamespace() {
        return this.idSegmentDistributorDefinition.getNamespace();
    }
    
    @Override
    public @NonNull String getName() {
        return this.idSegmentDistributorDefinition.getName();
    }
    
    @Override
    public long getStep() {
        return this.idSegmentDistributorDefinition.getStep();
    }
    
    @Override
    public GroupedKey group() {
        return this.ensureGroupedBinding().group();
    }
    
    @Override
    public long nextMaxId() {
        return this.ensureGroupedBinding().nextMaxId();
    }
    
    @Override
    public long nextMaxId(long step) {
        return this.ensureGroupedBinding().nextMaxId(step);
    }
    
    @Override
    public @NonNull IdSegment nextIdSegment() {
        return this.ensureGroupedBinding().nextIdSegment();
    }
    
    @Override
    public @NonNull IdSegment nextIdSegment(long ttl) {
        return this.ensureGroupedBinding().nextIdSegment(ttl);
    }
    
    @Override
    public @NonNull IdSegment nextIdSegment(int segments, long ttl) {
        return this.ensureGroupedBinding().nextIdSegment(segments, ttl);
    }
    
    @Override
    public @NonNull IdSegmentChain nextIdSegmentChain(IdSegmentChain previousChain, int segments, long ttl) {
        return this.ensureGroupedBinding().nextIdSegmentChain(previousChain, segments, ttl);
    }
    
    @Override
    public @NonNull IdSegmentChain nextIdSegmentChain(IdSegmentChain previousChain) {
        return this.ensureGroupedBinding().nextIdSegmentChain(previousChain);
    }
    
    public static class GroupedBinding implements GroupedIdSegmentDistributor {
        
        private final GroupedKey group;
        private final IdSegmentDistributor idSegmentDistributor;
        
        public GroupedBinding(GroupedKey group, IdSegmentDistributor idSegmentDistributor) {
            this.group = group;
            this.idSegmentDistributor = idSegmentDistributor;
        }
        
        @Override
        public GroupedKey group() {
            return group;
        }
        
        @Override
        public @NonNull String getNamespace() {
            return idSegmentDistributor.getNamespace();
        }
        
        @Override
        public @NonNull String getName() {
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
        
        private long getMinTtl(long ttl) {
            long groupedTtl = group.ttl();
            return Math.min(groupedTtl, ttl);
        }
        
        @Override
        public @NonNull IdSegment nextIdSegment(long ttl) {
            long minTtl = getMinTtl(ttl);
            return GroupedIdSegmentDistributor.super.nextIdSegment(minTtl);
        }
        
        @Override
        public @NonNull IdSegment nextIdSegment(int segments, long ttl) {
            long minTtl = getMinTtl(ttl);
            return GroupedIdSegmentDistributor.super.nextIdSegment(segments, minTtl);
        }
        
        @Override
        public @NonNull IdSegmentChain nextIdSegmentChain(IdSegmentChain previousChain, int segments, long ttl) {
            long minTtl = getMinTtl(ttl);
            return GroupedIdSegmentDistributor.super.nextIdSegmentChain(previousChain, segments, minTtl);
        }
    }
}
