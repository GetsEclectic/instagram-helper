create table instagram_user_json (
    USER_PK bigint primary key,
    JSON jsonb,
    INSERT_DATE timestamp with time zone not null default current_timestamp
);
create index instagram_user_json_json_idx on instagram_user_json using gin (JSON);