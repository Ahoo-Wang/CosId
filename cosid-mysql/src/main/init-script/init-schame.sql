create table if not exists cosid
(
    name            varchar(100) not null comment '{namespace}.{name}',
    last_max_id     bigint       not null,
    last_fetch_time bigint       not null default unix_timestamp(),
    primary key (name)
) engine = InnoDB;

