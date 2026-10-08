-- Abuse and account deletion tests for M6 (PRD section 12). Run with `supabase test db` or
-- `supabase/tests/run_live.ps1`. Everything is rolled back.
begin;

create extension if not exists pgtap with schema extensions;

select plan(20);

insert into auth.users (id, email, raw_user_meta_data)
values
    ('bbbbbbbb-0000-0000-0000-000000000001', 'first@example.com', '{"name": "First"}'),
    ('bbbbbbbb-0000-0000-0000-000000000002', 'again@example.com', '{"name": "Again"}'),
    ('bbbbbbbb-0000-0000-0000-000000000003', 'other@example.com', '{"name": "Other"}'),
    ('bbbbbbbb-0000-0000-0000-000000000004', 'free@example.com', '{"name": "Free"}'),
    ('bbbbbbbb-0000-0000-0000-000000000005', 'boss@example.com', '{"name": "Boss"}'),
    ('bbbbbbbb-0000-0000-0000-000000000006', 'payer@example.com', '{"name": "Payer"}');

-- Boss is the only enabled Admin for these tests.
update public.profiles set role = 'user' where id <> 'bbbbbbbb-0000-0000-0000-000000000005';
update public.profiles set role = 'admin' where id = 'bbbbbbbb-0000-0000-0000-000000000005';

-- Trial reuse after deleting the account ------------------------------------------------------------------

select is(public.claim_trial('bbbbbbbb-0000-0000-0000-000000000001', 'email-a', 'device-a') ->> 'result', 'granted',
    'The first account gets the trial');
select public.reserve_separation('bbbbbbbb-0000-0000-0000-000000000001', '[{"jobRef": "first-1"}]');
insert into public.payment_events (user_id, provider, provider_event_id, event_type)
values ('bbbbbbbb-0000-0000-0000-000000000001', 'razorpay', 'evt-abuse-1', 'payment.captured');

select is(public.delete_account('bbbbbbbb-0000-0000-0000-000000000001') ->> 'result', 'deleted',
    'A user can delete their account');
select is((select count(*)::int from auth.users where id = 'bbbbbbbb-0000-0000-0000-000000000001'), 0,
    'The auth user is gone');
select is(
    (select count(*)::int from public.profiles where id = 'bbbbbbbb-0000-0000-0000-000000000001')
    + (select count(*)::int from public.trials where user_id = 'bbbbbbbb-0000-0000-0000-000000000001')
    + (select count(*)::int from public.ai_usage where user_id = 'bbbbbbbb-0000-0000-0000-000000000001'),
    0,
    'Profile, trial and usage are deleted with it'
);
select ok(exists (select 1 from public.trial_claims where email_hash = 'email-a' and user_id is null),
    'The trial claim is kept without the user id');
select ok(exists (select 1 from public.payment_events where provider_event_id = 'evt-abuse-1' and user_id is null),
    'Payment records are kept without the user id');
select ok(exists (
    select 1 from public.admin_audit_log
    where action = 'account_deleted' and target_user_id = 'bbbbbbbb-0000-0000-0000-000000000001'
      and actor_id is null and not (details ? 'email')
), 'The deletion is audited without the email');

select is(public.claim_trial('bbbbbbbb-0000-0000-0000-000000000002', 'email-a', 'device-b') ->> 'reason', 'email',
    'Signing up again with the same email gets no second trial');
select is(public.claim_trial('bbbbbbbb-0000-0000-0000-000000000003', 'email-c', 'device-a') ->> 'reason', 'device',
    'Another email on the same phone gets no second trial');

-- Account deletion refusals --------------------------------------------------------------------------------

select is(public.delete_account('bbbbbbbb-0000-0000-0000-000000000005') ->> 'error', 'last_admin',
    'The last enabled Admin cannot delete their account');
insert into public.subscriptions (user_id, status, provider, provider_ref, expires_at)
values ('bbbbbbbb-0000-0000-0000-000000000006', 'active', 'razorpay', 'sub-abuse', now() + interval '20 days');
select is(public.delete_account('bbbbbbbb-0000-0000-0000-000000000006') ->> 'error', 'active_subscription',
    'A renewing paid subscription has to be cancelled first');
update public.subscriptions set cancel_at_period_end = true where provider_ref = 'sub-abuse';
select is(public.delete_account('bbbbbbbb-0000-0000-0000-000000000006') ->> 'result', 'deleted',
    'A cancelled subscription does not block deletion');
select is(public.delete_account('bbbbbbbb-0000-0000-0000-000000000006') ->> 'error', 'not_found',
    'Deleting twice finds nothing');

-- Usage tricks ---------------------------------------------------------------------------------------------

select public.reserve_separation('bbbbbbbb-0000-0000-0000-000000000004',
    (select jsonb_agg(jsonb_build_object('jobRef', 'f-' || i)) from generate_series(1, 10) i));
select is(public.finish_separation('bbbbbbbb-0000-0000-0000-000000000004', 'f-1', 'completed') -> 'usage' ->> 'used', '1',
    'A completed job counts');
select is(public.finish_separation('bbbbbbbb-0000-0000-0000-000000000004', 'f-1', 'released') -> 'usage' ->> 'used', '1',
    'Releasing a completed job does not give the use back');
select is(
    jsonb_array_length(public.reserve_separation('bbbbbbbb-0000-0000-0000-000000000004', '[{"jobRef": "f-1"}, {"jobRef": "f-11"}]') -> 'granted'),
    1,
    'Replaying a used job ref does not open a new slot'
);

select public.finish_separation('bbbbbbbb-0000-0000-0000-000000000003', 'f-2', 'released');
select is((select status from public.ai_usage where user_id = 'bbbbbbbb-0000-0000-0000-000000000004' and job_ref = 'f-2'),
    'reserved', 'Another user cannot release someone else''s job');

update public.profiles set disabled = true where id = 'bbbbbbbb-0000-0000-0000-000000000004';
select is(public.finish_separation('bbbbbbbb-0000-0000-0000-000000000004', 'f-3', 'released') -> 'usage' ->> 'used', '1',
    'A disabled account can still report outcomes but gains nothing');

-- What the app roles can call ------------------------------------------------------------------------------

select is(
    (select array_agg(p.proname::text order by p.proname)
     from pg_proc p join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public' and p.prosecdef and has_function_privilege('authenticated', p.oid, 'execute')),
    array['is_admin'],
    'is_admin() is the only security definer function the app can call'
);
select is(
    (select count(*)::int
     from pg_proc p join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public' and p.prosecdef and has_function_privilege('anon', p.oid, 'execute')),
    0,
    'Signed-out callers can call no security definer function'
);

select * from finish();
rollback;
