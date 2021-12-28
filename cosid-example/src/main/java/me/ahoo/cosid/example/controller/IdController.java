/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

package me.ahoo.cosid.example.controller;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.example.dto.AsStringDto;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.snowflake.SafeJavaScriptSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeFriendlyId;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * @author ahoo wang
 */
@RestController
@RequestMapping("id")
public class IdController {
    private final IdGeneratorProvider idGeneratorProvider;

    public IdController(IdGeneratorProvider idGeneratorProvider) {
        this.idGeneratorProvider = idGeneratorProvider;
    }

    private IdGenerator getIdGenerator(String name) {
        Preconditions.checkNotNull(name, "name can not be null");
        Optional<IdGenerator> optionalIdGenerator = idGeneratorProvider.get(name);
        if (!optionalIdGenerator.isPresent()) {
            throw new IllegalArgumentException(Strings.lenientFormat("idGenerator:[%s] not fond.", name));
        }
        return optionalIdGenerator.get();
    }

    @GetMapping("{name}")
    public Object generate(@PathVariable String name) {
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

    @GetMapping("shortId")
    public String shortId() {
        IdGenerator idGenerator = getIdGenerator("short_id");
        return idGenerator.generateAsString();
    }

    @GetMapping("asString")
    public AsStringDto asString() {
        IdGenerator idGenerator = getIdGenerator("short_id");
        long id = idGenerator.generate();
        AsStringDto dto = new AsStringDto();
        dto.setId(id);
        dto.setRadixId(id);
        dto.setRadixPadStartId(id);
        dto.setRadixPadStartCharSize10Id(id);
        dto.setFriendlyId(id);
        return dto;
    }

    @PostMapping("asStringDes")
    public AsStringDto asStringDes(@RequestBody AsStringDto dto) {
        return dto;
    }
}
