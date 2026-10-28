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
-- (alter table cosid_namespace add slot_offset integer not null default 0).
-- SQL Server 2019+. Text columns are nvarchar: the driver sends string parameters as nvarchar by default, and a
-- varchar key column would then be converted for every lookup instead of seeking its index. Key columns use the
-- binary collation Latin1_General_100_BIN2, so keys are case-sensitive; SQL Server still ignores trailing spaces when
-- comparing strings (whatever the collation), so the store rejects keys that end with a space instead of merging them.
if object_id(N'cosid_range', N'U') is null
create table cosid_range
(
    range_key  nvarchar(255) collate Latin1_General_100_BIN2 not null primary key,
    next_value bigint        not null,
    sealed     bit           not null default 0
);

if object_id(N'cosid_slot', N'U') is null
create table cosid_slot
(
    namespace      nvarchar(100) collate Latin1_General_100_BIN2 not null,
    slot           integer       not null,
    owner_key      nvarchar(255) collate Latin1_General_100_BIN2,
    incarnation    nchar(36),
    last_owner_key nvarchar(255) collate Latin1_General_100_BIN2,
    fence          bigint        not null default 0,
    expires_at     bigint        not null default 0,
    floor_ms       bigint        not null default 0,
    ceiling_ms     bigint        not null default 0,
    next_floor_ms  bigint        not null default 0,
    primary key (namespace, slot)
);

if not exists (select 1 from sys.indexes where name = N'cosid_slot_last_owner_key' and object_id = object_id(N'cosid_slot'))
create index cosid_slot_last_owner_key on cosid_slot (namespace, last_owner_key);

if object_id(N'cosid_namespace', N'U') is null
create table cosid_namespace
(
    namespace    nvarchar(100) collate Latin1_General_100_BIN2 not null primary key,
    slot_limit   integer       not null,
    tolerance_ms bigint        not null,
    slot_offset  integer       not null default 0
);
