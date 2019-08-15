alter table follower_log drop constraint follower_log_action_check;
alter table follower_log add constraint follower_log_action_check check ( action in ('followed', 'unfollowed') )