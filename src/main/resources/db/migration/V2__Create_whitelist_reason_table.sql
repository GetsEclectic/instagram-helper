create table WHITELIST_REASON (
    REASON varchar(128) primary key
);

insert into WHITELIST_REASON values ('scanned when pruning mutual followers');
insert into WHITELIST_REASON values ('manually whitelisted');