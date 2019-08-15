create sequence follower_log_id_seq;
create table follower_log (
    ID int primary key default nextval('follower_log_id_seq'),
    OUR_PK bigint not null,
    ACTION varchar(10) check ( ACTION in ('follow', 'unfollow') ),
    FOLLOWER_PK bigint not null,
    INSERT_DATE timestamp with time zone not null default current_timestamp
);