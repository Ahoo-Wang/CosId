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
-- ds0
create table cosid
(
    name varchar(100) not null comment '{namespace}.{name}'
        primary key,
    last_max_id bigint default 0 not null,
    last_fetch_time bigint not null
);

-- ds0 & ds1
create table t_date_log_202205
(
    id          bigint   not null,
    create_time datetime not null,
    constraint t_date_log_202205_pk
        primary key (id)
);


create table t_date_time_log_202205
(
    id          bigint   not null,
    create_time datetime not null,
    constraint t_date_time_log_202205_pk
        primary key (id)
);


create table t_timestamp_log_202205
(
    id          bigint   not null,
    create_time bigint not null,
    constraint t_timestamp_log_202205_pk
        primary key (id)
);



create table t_snowflake_log_202205
(
    id          bigint   not null,
    constraint t_snowflake_log_202205_pk
        primary key (id)
);
