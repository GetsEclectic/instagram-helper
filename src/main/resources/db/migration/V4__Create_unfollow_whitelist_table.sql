create sequence unfollow_whitelist_id_seq;
create table UNFOLLOW_WHITELIST (
    ID int primary key default nextval('unfollow_whitelist_id_seq'),
    OUR_PK bigint not null,
    WHITELISTED_PK bigint not null,
    WHITELIST_REASON varchar(128) not null references whitelist_reason(reason),
    INSERT_DATE timestamp with time zone not null default current_timestamp
);