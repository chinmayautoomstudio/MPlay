-- Separator usage limit tests for M3. Run with `supabase test db`.
begin;

create extension if not exists pgtap with schema extensions;

select plan(17);

insert into auth.users (id, email, raw_user_meta_data)
values
    ('11111111-1111-1111-1111-111111111111', 'free@example.com', '{"name": "Free"}'),
    ('22222222-2222-2222-2222-222222222222', 'trial@example.com', '{"name": "Trial"}'),
    ('33333333-3333-3333-3333-333333333333', 'off@example.com', '{"name": "Off"}');

insert into public.trials (user_id, started_at, ends_at)
values ('22222222-2222-2222-2222-222222222222', now(), now() + interval '30 days');
update public.profiles set disabled = true where id = '33333333-3333-3333-3333-333333333333';

-- Week boundary --------------------------------------------------------------------------------------------

select is(public.usage_week_start('2026-10-11 18:29:59+00'), '2026-10-05'::date,
    'Sunday 23:59:59 IST is still in the week that started on Monday 5 October');
select is(public.usage_week_start('2026-10-11 18:30:00+00'), '2026-10-12'::date,
    'Monday 00:00 IST starts a new week');

-- Free limit -----------------------------------------------------------------------------------------------

create temp table r as
select public.reserve_separation(
    '11111111-1111-1111-1111-111111111111',
    (select jsonb_agg(jsonb_build_object('jobRef', 'job-' || i, 'songRef', 'Song ' || i)) from generate_series(1, 12) i)
) as v;

select is(jsonb_array_length((select v -> 'granted' from r)), 10, 'Free gets 10 reservations');
select is((select v -> 'denied' from r), '["job-11", "job-12"]'::jsonb, 'The jobs past the limit are denied');
select is((select count(*)::integer from public.ai_usage where status = 'denied'), 2, 'Denied requests are recorded');
select is((select v -> 'usage' ->> 'remaining' from r), '0', 'Nothing remains');

select is(
    public.reserve_separation('11111111-1111-1111-1111-111111111111', '[{"jobRef": "job-1"}, {"jobRef": "job-12"}]') - 'usage',
    '{"granted": ["job-1"], "denied": ["job-12"]}'::jsonb,
    'Asking again for known jobs returns the earlier answers'
);

select is(public.finish_separation('11111111-1111-1111-1111-111111111111', 'job-1', 'released') -> 'usage' ->> 'remaining', '1',
    'A released reservation gives the use back');
select is(public.finish_separation('11111111-1111-1111-1111-111111111111', 'job-2', 'completed') -> 'usage' ->> 'used', '1',
    'A completed job counts as a use');
select is(public.finish_separation('11111111-1111-1111-1111-111111111111', 'job-2', 'completed') -> 'usage' ->> 'used', '1',
    'Reporting the same completion again changes nothing');

update public.ai_usage set status = 'completed', completed_at = now() - interval '8 days',
    week_start = public.usage_week_start(now() - interval '8 days')
where job_ref = 'job-3';
select is(public.usage_summary('11111111-1111-1111-1111-111111111111') ->> 'used', '1',
    'Uses from an earlier week do not count');

update public.ai_usage set reserved_at = now() - interval '49 hours' where job_ref = 'job-4';
select is(public.usage_summary('11111111-1111-1111-1111-111111111111') ->> 'remaining', '3',
    'A reservation older than 48 hours stops holding a use');

-- Trial and disabled ---------------------------------------------------------------------------------------

select is(
    jsonb_array_length(public.reserve_separation(
        '22222222-2222-2222-2222-222222222222',
        (select jsonb_agg(jsonb_build_object('jobRef', 't-' || i)) from generate_series(1, 15) i)
    ) -> 'granted'),
    15,
    'Trial is unlimited'
);
select is(public.finish_separation('22222222-2222-2222-2222-222222222222', 'offline-1', 'completed') -> 'usage' ->> 'unlimited', 'true',
    'Finishing a job with no reservation records it');
select is(public.reserve_separation('33333333-3333-3333-3333-333333333333', '[{"jobRef": "x"}]') ->> 'error', 'account',
    'A disabled account cannot reserve');

-- Access ---------------------------------------------------------------------------------------------------

set local role authenticated;
select set_config(
    'request.jwt.claims',
    '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}',
    true
);

select throws_ok(
    $$ select public.reserve_separation('11111111-1111-1111-1111-111111111111', '[{"jobRef": "y"}]') $$,
    '42501',
    null,
    'The app cannot reserve directly'
);
select throws_ok(
    $$ select public.finish_separation('11111111-1111-1111-1111-111111111111', 'job-5', 'completed') $$,
    '42501',
    null,
    'The app cannot report outcomes directly'
);

reset role;

select * from finish();
rollback;
