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

package me.ahoo.cosid.rest.entity;

import me.ahoo.cosid.annotation.CosId;

/**
 * create table t_order
 * (
 * id          bigint      not null
 * primary key,
 * string_id   varchar(20) not null,
 * friendly_id varchar(25) not null,
 * biz_id      bigint      not null
 * );
 *
 * @author ahoo wang
 */
public class Order {

    @CosId
    private long id;

    @CosId
    private String stringId;

    @CosId(friendlyId = true)
    private String friendlyId;

    @CosId(value = "bizC")
    private long bizId;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getStringId() {
        return stringId;
    }

    public void setStringId(String stringId) {
        this.stringId = stringId;
    }

    public String getFriendlyId() {
        return friendlyId;
    }

    public void setFriendlyId(String friendlyId) {
        this.friendlyId = friendlyId;
    }

    public long getBizId() {
        return bizId;
    }

    public void setBizId(long bizId) {
        this.bizId = bizId;
    }
}
