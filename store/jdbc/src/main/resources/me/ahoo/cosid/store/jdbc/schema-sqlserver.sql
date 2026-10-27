-- The CosId 5 JDBC schema (docs/architecture/06-stores.md §2.1). Keys and slots are never deleted (D23).
-- Run as is for the default table names; options.initSchema runs it with options.rangeTable / slotTable /
-- namespaceTable in place of cosid_range / cosid_slot / cosid_namespace.
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

if object_id(N'cosid_namespace', N'U') is null
create table cosid_namespace
(
    namespace    nvarchar(100) collate Latin1_General_100_BIN2 not null primary key,
    slot_limit   integer       not null,
    tolerance_ms bigint        not null
);
