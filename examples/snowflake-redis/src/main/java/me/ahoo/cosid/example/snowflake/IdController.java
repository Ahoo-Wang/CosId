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

package me.ahoo.cosid.example.snowflake;

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.PartsIdGenerator;
import me.ahoo.cosid.codec.PartsCodec;
import me.ahoo.cosid.runtime.CosIdRuntime;
import me.ahoo.cosid.time.IdParts;
import me.ahoo.cosid.time.TimeBasedGenerator;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Hands out the ids. The {@code default} generator is the primary {@code IdGenerator} bean.
 */
@RestController
public class IdController {
    private final CosIdRuntime runtime;
    private final IdGenerator ids;
    private final PartsIdGenerator token;

    /**
     * The controller.
     *
     * @param runtime the runtime
     * @param ids     the {@code default} generator
     * @param token   the {@code token} generator
     */
    public IdController(CosIdRuntime runtime, IdGenerator ids, @Qualifier("token") PartsIdGenerator token) {
        this.runtime = runtime;
        this.ids = ids;
        this.token = token;
    }

    /**
     * The next id, its text, and its parts unpacked by the layout.
     *
     * @return the id, the text, the time, the slot and the sequence
     */
    @GetMapping("/ids/next")
    public Map<String, Object> next() {
        long id = ids.generate();
        TimeBasedGenerator generator = runtime.get("default", TimeBasedGenerator.class);
        IdParts parts = generator.layout().unpack(id);
        return Map.of("id", id,
            "text", runtime.stringGenerator("default").codec().encode(id),
            "issuedAt", parts.instant(generator.layout()).toString(),
            "slot", parts.node(),
            "sequence", parts.seq());
    }

    /**
     * The next trace id, readable: time, slot and sequence.
     *
     * @return the text
     */
    @GetMapping("/traces/next")
    public Map<String, Object> nextTrace() {
        return Map.of("text", runtime.stringGenerator("trace").generate());
    }

    /**
     * The next 80-bit token, through its {@code PartsCodec}.
     *
     * @return the text
     */
    @GetMapping("/tokens/next")
    public Map<String, Object> nextToken() {
        return Map.of("text", runtime.get("token", PartsCodec.class).encode(token.nextParts()));
    }
}
