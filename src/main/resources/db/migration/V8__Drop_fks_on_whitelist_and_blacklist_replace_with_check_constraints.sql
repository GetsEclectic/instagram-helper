alter table follow_blacklist drop constraint follow_blacklist_blacklist_reason_fkey;
alter table unfollow_whitelist drop constraint unfollow_whitelist_whitelist_reason_fkey;
alter table follow_blacklist add constraint blacklist_reason_in check ( blacklist_reason in ('scanned when copying followers') );
alter table unfollow_whitelist add constraint whitelist_reason_in check ( whitelist_reason in ('manually whitelisted', 'scanned when pruning mutual followers'));
drop table blacklist_reason;
drop table whitelist_reason;