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

package me.ahoo.cosid.example.segment;

import me.ahoo.cosid.GroupedId;
import me.ahoo.cosid.GroupedIdGenerator;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.codec.GroupedCodec;
import me.ahoo.cosid.runtime.CosIdRuntime;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Hands out the ids. Each generator is a bean named after it; the runtime gives the text views and the filler.
 */
@RestController
public class IdController {
    private final CosIdRuntime runtime;
    private final IdGenerator order;
    private final GroupedIdGenerator bill;

    /**
     * The controller.
     *
     * @param runtime the runtime
     * @param order   the {@code order} generator bean
     * @param bill    the {@code bill} generator bean
     */
    public IdController(CosIdRuntime runtime, @Qualifier("order") IdGenerator order, @Qualifier("bill") GroupedIdGenerator bill) {
        this.runtime = runtime;
        this.order = order;
        this.bill = bill;
    }

    /**
     * The next order id, as a number and through the generator's codec.
     *
     * @return the id and its text
     */
    @GetMapping("/orders/next")
    public Map<String, Object> nextOrder() {
        long id = order.generate();
        return Map.of("id", id, "text", runtime.stringGenerator("order").codec().encode(id));
    }

    /**
     * The next bill number: today's period and its value, and the text of the {@code GroupedCodec}.
     *
     * @return the period, the value and the text
     */
    @GetMapping("/bills/next")
    public Map<String, Object> nextBill() {
        GroupedId id = bill.generate();
        return Map.of("period", id.period(), "value", id.value(), "text", runtime.get("bill", GroupedCodec.class).encode(id));
    }

    /**
     * Fills the id of a new order.
     *
     * @param order the order
     * @return the order with its id
     */
    @PostMapping("/orders")
    public Order create(@RequestBody Order order) {
        runtime.filler().fill(order);
        return order;
    }
}
