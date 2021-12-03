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

package me.ahoo.cosid.rest.repository;

import me.ahoo.cosid.rest.entity.Order;
import me.ahoo.cosid.rest.entity.OrderItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @author ahoo wang
 */
@Mapper
public interface OrderRepository {

    @Insert("insert into t_order (order_id,user_id) value (#{orderId},#{userId});")
    void insert(Order order);

    @Insert("insert into t_order_item (order_item_id,order_id) value (#{orderItemId},#{orderId});")
    void insertItem(OrderItem orderItem);

    @Select("select * from t_order where order_id=#{orderId}")
    Order getById(@Param("orderId") long orderId);
}
