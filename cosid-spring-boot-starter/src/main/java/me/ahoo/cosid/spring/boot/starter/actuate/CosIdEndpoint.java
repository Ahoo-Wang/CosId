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
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.stat.Stat;
import me.ahoo.cosid.stat.Statistical;

import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

import java.util.HashMap;
import java.util.Map;

@Endpoint(id = CosId.COSID)
public class CosIdEndpoint {
    private final IdGeneratorProvider idGeneratorProvider;

    public CosIdEndpoint(IdGeneratorProvider idGeneratorProvider) {
        this.idGeneratorProvider = idGeneratorProvider;
    }

    @ReadOperation
    public Map<String, Stat> stat() {
        Map<String, Stat> statMap = new HashMap<>();
        for (Map.Entry<String, IdGenerator> entry : idGeneratorProvider.entries()) {
            var stat = entry.getValue().stat();
            statMap.put(entry.getKey(), stat);
        }
        return statMap;
    }

    @ReadOperation
    public Stat getStat(@Selector String name) {
        var idGenerator = idGeneratorProvider.getRequired(name);
        return idGenerator.stat();
    }

    @DeleteOperation
    public Stat remove(@Selector String name) {
        var idGenerator = idGeneratorProvider.remove(name);
        return idGenerator.stat();
    }


}
