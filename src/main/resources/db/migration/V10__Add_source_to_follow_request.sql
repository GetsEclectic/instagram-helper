alter table follow_request
    add column source varchar(128),
    add column source_type varchar(128) check ( source_type in ('tag_like', 'user') );