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

-- percent of follow requests in the last 30 days that resulted in at least one like, by user, by day
select
	fr.our_pk,
	date_trunc('day', fr.insert_date),
	sum(case when exists (select 1 from liker_log ll where ll.liker_pk = fr.requested_pk) then 1 else 0 end) / count(1)::decimal percent_liked,
	count(1) num_follow_requests
from
	follow_request fr
where
	fr.insert_date > current_date - 30
group by
	fr.our_pk,
	date_trunc('day', fr.insert_date)
order by
	fr.our_pk,
	date_trunc('day', fr.insert_date);

-- percent of follow requests since copy from tag vs copy from user ab test started that resulted in at least one like, by source_type, by day
select
	fr.source_type,
	date_trunc('day', fr.insert_date),
	sum(case when exists (select 1 from liker_log ll where ll.liker_pk = fr.requested_pk) then 1 else 0 end) / count(1)::decimal percent_liked,
    sum(case when exists (select 1 from liker_log ll where ll.liker_pk = fr.requested_pk) then 1 else 0 end) num_liked,
	count(1) num_follow_requests
from
	follow_request fr
where
	fr.insert_date > date '2019-08-24'
group by
	fr.source_type,
	date_trunc('day', fr.insert_date)
order by
	fr.source_type,
	date_trunc('day', fr.insert_date);

-- export for ml test:
--   all the actions since 10-27-2019
--   did the requested user like one of our posts
--   did the requested user follow us
--   did the requested user like two or more of our posts, at least 18 hours apart, indicates engagement
--   the json describing the user, as returned by ApiClient.getInstagramUser
--   our pk
select
	al_earliest.*,
	case
		when exists (
		select
			1
		from
			liker_log ll
		where
			ll.liker_pk = al_earliest.requested_pk) then 1
		else 0
	end liked,
	case
		when exists (
		select
			1
		from
			follower_log fl
		where
			fl.follower_pk = al_earliest.requested_pk
			and fl."action" = 'followed') then 1
		else 0
	end followed_back,
	case
		when exists (
		select
			1
		from
			liker_log ll
		join liker_log ll_later on
			ll_later.liker_pk = ll.liker_pk
			and ll_later.insert_date > ll.insert_date + interval '18 hours'
		where
			ll.liker_pk = al_earliest.requested_pk) then 1
		else 0
	end engaged,
	iuj."json"
from
	action_log al_earliest
join instagram_user_json iuj on
	iuj.user_pk = al_earliest.requested_pk
where
	al_earliest.insert_date > date '2019-10-27'
	and not exists (
	select
		1
	from
		action_log al_earlier
	where
		al_earlier.requested_pk = al_earliest.requested_pk
		and al_earlier.id < al_earliest.id);