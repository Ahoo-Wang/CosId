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

package me.ahoo.cosid.spring.boot.starter.actuate;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.Decorator;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.stat.generator.IdGeneratorStat;

import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

/**
 * The {@code cosid} endpoint with its read operations and a delete operation that removes an id generator at runtime.
 * Removing a segment chain id also stops its background prefetch.
 * Turned into {@link CosIdReadOnlyEndpoint} with {@code cosid.actuator.delete.enabled=false}.
 */
@Endpoint(id = CosId.COSID)
public class CosIdEndpoint extends CosIdReadOnlyEndpoint {
    
    public CosIdEndpoint(IdGeneratorProvider idGeneratorProvider) {
        super(idGeneratorProvider);
    }
    
    @DeleteOperation
    public IdGeneratorStat remove(@Selector String name) {
        var idGenerator = idGeneratorProvider.getRequired(name);
        idGeneratorProvider.remove(name);
        var stat = idGenerator.stat();
        if (Decorator.getActual(idGenerator) instanceof SegmentChainId segmentChainId) {
            segmentChainId.close();
        }
        return stat;
    }
}
