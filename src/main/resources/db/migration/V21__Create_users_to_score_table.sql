create sequence users_to_score_id_seq;
create table users_to_score (
    ID int primary key default nextval('users_to_score_id_seq'),
    OUR_PK bigint not null,
    SCANNED_PK bigint not null,
    SOURCE varchar(128),
    INSERT_DATE timestamp with time zone not null default current_timestamp
);