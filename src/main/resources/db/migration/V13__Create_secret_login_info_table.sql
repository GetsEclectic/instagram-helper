create table secret_login_info (
    USER_PK bigint primary key,
    USERNAME varchar(256) not null,
    COOKIE_STORE_SERIALIZED bytea not null,
    UUID varchar(128) not null,
    INSERT_DATE timestamp with time zone not null default current_timestamp
);