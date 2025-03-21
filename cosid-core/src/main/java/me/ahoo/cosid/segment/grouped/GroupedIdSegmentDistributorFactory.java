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

import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;

import jakarta.annotation.Nonnull;

public class GroupedIdSegmentDistributorFactory implements IdSegmentDistributorFactory {
    private final GroupBySupplier groupBySupplier;
    private final IdSegmentDistributorFactory actual;
    
    public GroupedIdSegmentDistributorFactory(GroupBySupplier groupBySupplier, IdSegmentDistributorFactory actual) {
        this.groupBySupplier = groupBySupplier;
        this.actual = actual;
    }
    
    @Nonnull
    @Override
    public IdSegmentDistributor create(IdSegmentDistributorDefinition definition) {
        return new DefaultGroupedIdSegmentDistributor(groupBySupplier, definition, actual);
    }
}
