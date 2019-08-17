-- follower count at midnight each day by user
with date_range as (select generate_series ( '2019-08-01'::timestamp, current_date + 1, '1 day'::interval) as followers_at)
select
	fl.our_pk,
	followers_at,
	count(1)
from
	date_range dr
	join follower_log fl on fl.insert_date < dr.followers_at
where
	fl."action" = 'followed'
	and not exists (
	select
		1
	from
		follower_log fl_later_unfollow
	where
		fl_later_unfollow.our_pk = fl.our_pk
		and fl_later_unfollow.follower_pk = fl.follower_pk
		and fl_later_unfollow.id > fl.id
		and fl_later_unfollow."action" = 'unfollowed')
group by
	fl.our_pk, followers_at
	order by fl.our_pk, followers_at;

-- follows per day by user
select
	fl.our_pk,
	date_trunc('day', fl.insert_date),
	count(1)
from
	follower_log fl
where
	fl."action" = 'followed'
group by
	fl.our_pk,
	date_trunc('day', fl.insert_date)
order by
	fl.our_pk,
	date_trunc('day', fl.insert_date);

-- percent of follow requests in the last 3 days that resulted in at least one like by user
select
	fr.our_pk,
	sum(case when exists (select 1 from liker_log ll where ll.liker_pk = fr.requested_pk) then 1 else 0 end) / count(1)::decimal percent_liked,
	count(1) num_follow_requests
from
	follow_request fr
where
	fr.insert_date > current_date - 3
group by
	fr.our_pk;