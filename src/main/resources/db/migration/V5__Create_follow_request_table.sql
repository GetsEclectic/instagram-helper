create sequence follow_request_id_seq;
create table follow_request (
    ID int primary key default nextval('follow_request_id_seq'),
    OUR_PK bigint not null,
    REQUESTED_PK bigint not null,
    REQUESTED_USERNAME varchar(256) not null,
    INSERT_DATE timestamp with time zone not null default current_timestamp
);