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

import me.ahoo.cosid.proxy.api.ErrorResponse;
import me.ahoo.cosid.proxy.api.SegmentClient;
import me.ahoo.cosid.segment.AbstractIdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributor;

import com.google.common.base.Strings;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.web.client.HttpClientErrorException;

/**
 * ProxyIdSegmentDistributor .
 *
 * <p><img src="doc-files/CosId-Proxy.png" alt="CosId-Proxy"></p>
 *
 * @author ahoo wang
 */
@Slf4j
public class ProxyIdSegmentDistributor extends AbstractIdSegmentDistributor {
    private final SegmentClient segmentClient;
    
    public ProxyIdSegmentDistributor(SegmentClient segmentClient, String namespace, String name, long step) {
        super(namespace, name, step);
        this.segmentClient = segmentClient;
    }
    
    
    
    
    @SneakyThrows
    @Override
    protected long doNextMaxId(long step) {
        try {
            return segmentClient.nextMaxId(getNamespace(), getName(), step);
        } catch (HttpClientErrorException.Conflict conflict) {
            // The server detected that the max id moved backwards: same rule, and same exception, as the client's own check.
            ErrorResponse errorResponse = Jsons.OBJECT_MAPPER.readValue(conflict.getResponseBodyAsByteArray(), ErrorResponse.class);
            throw new IllegalStateException(Strings.lenientFormat("Unexpected code:[%s] - message:[%s].", errorResponse.getCode(), errorResponse.getMsg()),
                conflict);
        }
    }
}
