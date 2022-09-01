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
create database if not exists cosid_db;
use cosid_db;

create table if not exists cosid
(
    name            varchar(100)    not null comment '{namespace}.{name}',
    last_max_id     bigint unsigned not null default 0,
    last_fetch_time bigint unsigned not null default 0,
    constraint cosid_pk
    primary key (name)
    ) engine = InnoDB;

insert into cosid
(name, last_max_id, last_fetch_time)
    value
    ('namespace.name', 0, unix_timestamp());

create table if not exists cosid_machine
(
    name            varchar(100)     not null comment '{namespace}.{machine_id}',
    namespace       varchar(100)     not null,
    machine_id      integer unsigned not null default 0,
    last_timestamp  bigint unsigned  not null default 0,
    instance_id     varchar(100)     not null default '',
    distribute_time bigint unsigned  not null default 0,
    revert_time     bigint unsigned  not null default 0,
    constraint cosid_machine_pk
    primary key (name)
    ) engine = InnoDB;

create index idx_namespace on cosid_machine (namespace);
create index idx_instance_id on cosid_machine (instance_id);

create table t_friendly_table
(
    id varchar(25) not null primary key
);

create table t_table
(
    id bigint not null primary key
);


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


create table t_user_0
(
    id bigint not null
        primary key,
    age int null,
    name varchar(50) null,
    create_time datetime null
);

create table t_user_1
(
    id bigint not null
        primary key,
    age int null,
    name varchar(50) null,
    create_time datetime null
);

create table t_user_2
(
    id bigint not null
        primary key,
    age int null,
    name varchar(50) null,
    create_time datetime null
);

create table t_user_3
(
    id bigint not null
        primary key,
    age int null,
    name varchar(50) null,
    create_time datetime null
);

