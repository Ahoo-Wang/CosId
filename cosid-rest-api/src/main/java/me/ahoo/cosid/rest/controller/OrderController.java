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

package me.ahoo.cosid.rest.controller;

import me.ahoo.cosid.rest.entity.Order;
import me.ahoo.cosid.rest.repository.OrderRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * @author ahoo wang
 */
@RestController
@RequestMapping("orders")
public class OrderController {

    private final OrderRepository orderRepository;

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @PostMapping()
    public Order createOrder() {
        Order order = new Order();
        orderRepository.insert(order);
        /**
         * {
         *     "id": 212980826009239550,
         *     "stringId": "212980826009239553",
         *     "friendlyId": "20210803170945913-0-2",
         *     "bizId": 26996
         *   }
         */
        return order;
    }

    @PostMapping("/batch")
    public List<Order> createOrderBatch() {
        Order order = new Order();
        Order order1 = new Order();
        Order order2 = new Order();
        Order order3 = new Order();
        Order order4 = new Order();
        List<Order> list = Arrays.asList(order, order1, order2, order3, order4);
        orderRepository.insertList(list);
        return list;
    }
}
