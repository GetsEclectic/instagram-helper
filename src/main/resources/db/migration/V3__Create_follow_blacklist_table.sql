create sequence follow_blacklist_id_seq;
create table FOLLOW_BLACKLIST (
    ID int primary key default nextval('follow_blacklist_id_seq'),
    PK bigint not null,
    BLACKLIST_REASON varchar(128) not null references blacklist_reason(reason),
    INSERT_DATE timestamp with time zone not null
);