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

package me.ahoo.cosid.proxy;

import me.ahoo.cosid.proxy.api.SegmentApi;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Nonnull;

/**
 * ProxyIdSegmentDistributorFactory .
 *
 * @author ahoo wang
 */
@Slf4j
public class ProxyIdSegmentDistributorFactory implements IdSegmentDistributorFactory {
    
    private final SegmentApi segmentApi;
    

    public ProxyIdSegmentDistributorFactory(SegmentApi segmentApi) {
        this.segmentApi = segmentApi;
    }
    
    @Nonnull
    @SneakyThrows
    @Override
    public IdSegmentDistributor create(IdSegmentDistributorDefinition definition) {
        if (log.isInfoEnabled()) {
            log.info("Create [{}] .", definition.getNamespacedName());
        }
        segmentApi.createDistributor(definition.getNamespace(), definition.getName(), definition.getOffset(), definition.getStep());

        return new ProxyIdSegmentDistributor(segmentApi, definition.getNamespace(), definition.getName(), definition.getStep());
    }
}
