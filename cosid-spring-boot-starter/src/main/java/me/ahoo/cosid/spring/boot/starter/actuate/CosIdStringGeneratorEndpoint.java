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
import me.ahoo.cosid.provider.IdGeneratorProvider;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

@Endpoint(id = CosId.COSID + "StringGenerator")
public class CosIdStringGeneratorEndpoint {
    private final IdGeneratorProvider idGeneratorProvider;
    
    public CosIdStringGeneratorEndpoint(IdGeneratorProvider idGeneratorProvider) {
        this.idGeneratorProvider = idGeneratorProvider;
    }
    
    @ReadOperation
    public String shareGenerateAsString() {
        return idGeneratorProvider.getShare().generateAsString();
    }
    
    @ReadOperation
    public String generateAsString(@Selector String name) {
        return idGeneratorProvider.getRequired(name).generateAsString();
    }
    
}
