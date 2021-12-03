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
-- ds0
create table t_friendly_table
(
    id varchar(25) not null primary key
);

create table t_table_0
(
    id bigint not null primary key
);
create table t_table_1
(
    id bigint not null primary key
);

-- ds0 & ds1
create table t_order
(
    order_id      bigint not null primary key,
    user_id bigint not null
);

create table t_order_item
(
    order_item_id       bigint not null primary key,
    order_id bigint not null
);
