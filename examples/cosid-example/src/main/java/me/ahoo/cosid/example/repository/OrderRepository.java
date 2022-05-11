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

package me.ahoo.cosid.example.repository;

import me.ahoo.cosid.example.entity.Order;
import me.ahoo.cosid.example.entity.OrderItem;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author ahoo wang
 */
@Mapper
public interface OrderRepository {

    void insert(Order order);

    void insertItem(OrderItem orderItem);

    Order getById(@Param("orderId") long orderId);

    List<Order> getByIds(@Param("ids") List<Long> ids);
}
