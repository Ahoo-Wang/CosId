-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--      http://www.apache.org/licenses/LICENSE-2.0
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- The CosId 5 JDBC schema (docs/architecture/06-stores.md §2.1). Keys and slots are never deleted (D23).
-- Run as is for the default table names; options.initSchema runs it with options.rangeTable / slotTable /
-- namespaceTable in place of cosid_range / cosid_slot / cosid_namespace.
-- cosid_slot_last_owner_key, the index of an acquire's own rows (R1-jdbc-01), came after 5.0.0-alpha.1: initSchema adds it
-- to an older table too.
-- cosid_namespace.slot_offset came after 5.0.0-alpha.1: initSchema adds it to an older table
-- (alter table cosid_namespace add column slot_offset integer not null default 0).
-- MariaDB 10.11+.
-- Keys compare exactly: the key columns use a binary collation that also keeps trailing spaces (NO PAD), so `Order`,
-- `order` and `order ` are three keys, as on every other backend.
create table if not exists cosid_range
(
    range_key  varchar(255) character set utf8mb4 collate utf8mb4_nopad_bin not null primary key,
    next_value bigint       not null,
    sealed     boolean      not null default false
) engine = InnoDB;

create table if not exists cosid_slot
(
    namespace      varchar(100) character set utf8mb4 collate utf8mb4_nopad_bin not null,
    slot           integer      not null,
    owner_key      varchar(255) character set utf8mb4 collate utf8mb4_nopad_bin,
    incarnation    char(36),
    last_owner_key varchar(255) character set utf8mb4 collate utf8mb4_nopad_bin,
    fence          bigint       not null default 0,
    expires_at     bigint       not null default 0,
    floor_ms       bigint       not null default 0,
    ceiling_ms     bigint       not null default 0,
    next_floor_ms  bigint       not null default 0,
    primary key (namespace, slot),
    index cosid_slot_last_owner_key (namespace, last_owner_key)
) engine = InnoDB;

create table if not exists cosid_namespace
(
    namespace    varchar(100) character set utf8mb4 collate utf8mb4_nopad_bin not null primary key,
    slot_limit   integer      not null,
    tolerance_ms bigint       not null,
    slot_offset  integer      not null default 0
) engine = InnoDB;
