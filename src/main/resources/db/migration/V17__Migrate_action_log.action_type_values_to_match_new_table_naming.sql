update action_log set action_type = 'follow_tag_liker' where action_type = 'tag_like';
update action_log set action_type = 'follow_user_follower' where action_type = 'user';