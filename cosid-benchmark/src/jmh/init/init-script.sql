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

create database if not exists benchmark_db;
use benchmark_db;

# CosId
create table if not exists cosid
(
    name            varchar(100) not null comment '{namespace}.{name}',
    last_max_id     bigint       not null default 0,
    last_fetch_time bigint       not null,
    constraint cosid_pk
        primary key (name)
) engine = InnoDB;

# insert into cosid
# (name, last_max_id, last_fetch_time)
#     value
#     ('namespace.name', 0, unix_timestamp());


# Leaf

CREATE TABLE `leaf_alloc`
(
    `biz_tag`     varchar(128) NOT NULL DEFAULT '',
    `max_id`      bigint(20)   NOT NULL DEFAULT '1',
    `step`        int(11)      NOT NULL,
    `description` varchar(256)          DEFAULT NULL,
    `update_time` timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`biz_tag`)
) ENGINE = InnoDB;

# insert into leaf_alloc(biz_tag, max_id, step, description)
# values ('leaf-segment-test', 1, 2000, 'Test leaf Segment Mode Get Id')

#
