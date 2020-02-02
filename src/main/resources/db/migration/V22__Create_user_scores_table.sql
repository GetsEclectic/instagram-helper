create sequence user_scores_id_seq;
create table user_scores (
    ID int primary key default nextval('user_scores_id_seq'),
    OUR_PK bigint not null,
    USER_PK bigint not null,
    SCORE float not null,
    INSERT_DATE timestamp with time zone not null default current_timestamp
);