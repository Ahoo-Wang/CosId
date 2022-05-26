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

package me.ahoo.cosid.example.jdbc.controller;

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.snowflake.SafeJavaScriptSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeFriendlyId;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * IdController.
 *
 * @author ahoo wang
 */
@RestController
@RequestMapping("ids")
public class IdController {
    private final IdGeneratorProvider provider;

    public IdController() {
        this.provider = DefaultIdGeneratorProvider.INSTANCE;
    }

    private IdGenerator getIdGenerator(String name) {
        Preconditions.checkNotNull(name, "name can not be null");
        Optional<IdGenerator> optionalIdGenerator = provider.get(name);
        if (!optionalIdGenerator.isPresent()) {
            throw new IllegalArgumentException(Strings.lenientFormat("idGenerator:[%s] not fond.", name));
        }
        return optionalIdGenerator.get();
    }

    @GetMapping
    public long generate() {
        return provider
            .getShare()
            .generate();
    }

    @GetMapping("/as-string")
    public String generateAsString() {
        return provider
            .getShare()
            .generateAsString();
    }


    @GetMapping("{name}")
    public Object generateByName(@PathVariable String name) {
        IdGenerator idGenerator = getIdGenerator(name);
        long id = idGenerator.generate();
        if (SafeJavaScriptSnowflakeId.isSafeJavaScript(id)) {
            return id;
        }
        return String.valueOf(id);
    }

    @GetMapping("{name}/friendlyId")
    public String friendlyId(@PathVariable String name) {
        IdGenerator idGenerator = getIdGenerator(name);
        if (idGenerator instanceof SnowflakeFriendlyId) {
            return idGenerator.generateAsString();
        }
        throw new IllegalArgumentException(Strings.lenientFormat("idGenerator:[%s] is not SnowflakeFriendlyId.", name));
    }

}
