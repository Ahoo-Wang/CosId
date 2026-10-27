-- The CosId 5 JDBC schema (docs/architecture/06-stores.md §2.1). Keys and slots are never deleted (D23).
-- Run as is for the default table names; options.initSchema runs it with options.rangeTable / slotTable /
-- namespaceTable in place of cosid_range / cosid_slot / cosid_namespace.
-- MySQL 8.4+ and MariaDB 10.11+.
create table if not exists cosid_range
(
    range_key  varchar(255) not null primary key,
    next_value bigint       not null,
    sealed     boolean      not null default false
) engine = InnoDB;

create table if not exists cosid_slot
(
    namespace      varchar(100) not null,
    slot           integer      not null,
    owner_key      varchar(255),
    incarnation    char(36),
    last_owner_key varchar(255),
    fence          bigint       not null default 0,
    expires_at     bigint       not null default 0,
    floor_ms       bigint       not null default 0,
    ceiling_ms     bigint       not null default 0,
    next_floor_ms  bigint       not null default 0,
    primary key (namespace, slot)
) engine = InnoDB;

create table if not exists cosid_namespace
(
    namespace    varchar(100) not null primary key,
    slot_limit   integer      not null,
    tolerance_ms bigint       not null
) engine = InnoDB;
