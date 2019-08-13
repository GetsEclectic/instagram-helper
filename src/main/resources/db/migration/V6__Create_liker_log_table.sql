create sequence liker_log_id_seq;
create table liker_log (
    ID int primary key default nextval('liker_log_id_seq'),
    OUR_PK bigint not null,
    MEDIA_ID bigint not null,
    LIKER_PK bigint not null,
    INSERT_DATE timestamp with time zone not null default current_timestamp
);