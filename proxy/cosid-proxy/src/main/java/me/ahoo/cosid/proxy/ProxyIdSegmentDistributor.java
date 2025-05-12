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

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Nonnull;

/**
 * ProxyIdSegmentDistributor .
 *
 * <p><img src="doc-files/CosId-Proxy.png" alt="CosId-Proxy"></p>
 *
 * @author ahoo wang
 */
@Slf4j
public class ProxyIdSegmentDistributor implements IdSegmentDistributor {
    private final SegmentApi segmentApi;
    private final String namespace;
    private final String name;
    private final long step;
    
    public ProxyIdSegmentDistributor(SegmentApi segmentApi, String namespace, String name, long step) {
        this.segmentApi = segmentApi;
        this.namespace = namespace;
        this.name = name;
        this.step = step;
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
    
    @SneakyThrows
    @Override
    public long nextMaxId(long step) {
        return segmentApi.nextMaxId(getNamespace(), getName(), step);
    }
}
