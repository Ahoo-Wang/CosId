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

package me.ahoo.cosid.example.server;

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.runtime.CosIdRuntime;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Hands out ids of a Snowflake on a slot leased through the server and of a segment reserved through it.
 */
@RestController
public class IdController {
    private final CosIdRuntime runtime;
    private final IdGenerator ids;
    private final IdGenerator order;

    /**
     * The controller.
     *
     * @param runtime the runtime
     * @param ids     the {@code default} generator
     * @param order   the {@code order} generator
     */
    public IdController(CosIdRuntime runtime, IdGenerator ids, @Qualifier("order") IdGenerator order) {
        this.runtime = runtime;
        this.ids = ids;
        this.order = order;
    }

    /**
     * The next Snowflake id and the slot the server leased.
     *
     * @return the id and the slot
     */
    @GetMapping("/ids/next")
    public Map<String, Object> next() {
        return Map.of("id", ids.generate(), "slot", runtime.slot().orElseThrow().slot());
    }

    /**
     * The next order id.
     *
     * @return the id
     */
    @GetMapping("/orders/next")
    public Map<String, Object> nextOrder() {
        return Map.of("id", order.generate());
    }
}
