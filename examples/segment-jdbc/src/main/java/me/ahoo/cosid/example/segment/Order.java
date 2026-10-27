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

import me.ahoo.cosid.accessor.CosId;

/**
 * An entity whose id the {@code IdFiller} sets from the {@code order} generator, as a MyBatis or Spring Data JDBC
 * integration would before an insert.
 */
public class Order {
    @CosId("order")
    private long id;
    private String item;

    /**
     * The id.
     *
     * @return the id, 0 until filled
     */
    public long getId() {
        return id;
    }

    /**
     * Sets the id.
     *
     * @param id the id
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * The item.
     *
     * @return the item
     */
    public String getItem() {
        return item;
    }

    /**
     * Sets the item.
     *
     * @param item the item
     */
    public void setItem(String item) {
        this.item = item;
    }
}
