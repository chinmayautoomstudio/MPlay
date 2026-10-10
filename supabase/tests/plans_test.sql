-- Plan computation and trial claim tests for M2. Run with `supabase test db`.
begin;

create extension if not exists pgtap with schema extensions;

select plan(16);

insert into auth.users (id, email, raw_user_meta_data)
values
    ('11111111-1111-1111-1111-111111111111', 'alice@example.com', '{"name": "Alice"}'),
    ('22222222-2222-2222-2222-222222222222', 'bob@example.com', '{"name": "Bob"}'),
    ('33333333-3333-3333-3333-333333333333', 'carol@example.com', '{"name": "Carol"}'),
    ('44444444-4444-4444-4444-444444444444', 'dave@example.com', '{"name": "Dave"}');

-- Trials ---------------------------------------------------------------------------------------------------

select is(public.compute_entitlements('11111111-1111-1111-1111-111111111111') ->> 'plan', 'free',
    'No trial and no subscription is Free');

select is(public.claim_trial('11111111-1111-1111-1111-111111111111', 'email-a', 'device-a') ->> 'result', 'granted',
    'The first claim grants the trial');
select is(public.compute_entitlements('11111111-1111-1111-1111-111111111111') ->> 'plan', 'trial',
    'A running trial is Trial');
select is(
    (select ends_at - started_at from public.trials where user_id = '11111111-1111-1111-1111-111111111111'),
    interval '30 days',
    'The trial lasts 30 days'
);
select is(public.claim_trial('11111111-1111-1111-1111-111111111111', 'email-a', 'device-a') ->> 'result', 'existing',
    'Claiming again returns the existing trial');

select is(public.claim_trial('22222222-2222-2222-2222-222222222222', 'email-a', 'device-b') ->> 'reason', 'email',
    'The same normalized email cannot get a second trial');
select is(public.claim_trial('22222222-2222-2222-2222-222222222222', 'email-b', 'device-a') ->> 'reason', 'device',
    'The same device cannot get a second trial');
select is(public.compute_entitlements('22222222-2222-2222-2222-222222222222') ->> 'plan', 'free',
    'A denied claim leaves the user on Free');

update public.profiles set disabled = true where id = '44444444-4444-4444-4444-444444444444';
select is(public.claim_trial('44444444-4444-4444-4444-444444444444', 'email-d', 'device-d') ->> 'reason', 'account',
    'A disabled account cannot claim a trial');

update public.trials set started_at = now() - interval '31 days', ends_at = now() - interval '1 day'
where user_id = '11111111-1111-1111-1111-111111111111';
select is(public.compute_entitlements('11111111-1111-1111-1111-111111111111') ->> 'plan', 'free',
    'An ended trial is Free');

-- Subscriptions --------------------------------------------------------------------------------------------

insert into public.subscriptions (user_id, status, provider, provider_ref, expires_at)
values ('33333333-3333-3333-3333-333333333333', 'active', 'payu', 'sub_c1', now() + interval '20 days');
select is(public.compute_entitlements('33333333-3333-3333-3333-333333333333') ->> 'plan', 'pro',
    'An active subscription is Pro');

update public.subscriptions set status = 'cancelled', cancel_at_period_end = true where provider_ref = 'sub_c1';
select is(public.compute_entitlements('33333333-3333-3333-3333-333333333333') ->> 'plan', 'pro',
    'A cancelled subscription stays Pro until the paid period ends');

update public.subscriptions set expires_at = now() - interval '1 hour' where provider_ref = 'sub_c1';
select is(public.compute_entitlements('33333333-3333-3333-3333-333333333333') ->> 'plan', 'free',
    'After the paid period ends the user is Free');

update public.subscriptions set status = 'expired' where provider_ref = 'sub_c1';
select is(public.compute_entitlements('33333333-3333-3333-3333-333333333333') ->> 'subscription', null::text,
    'An expired subscription is not reported');

-- Access ---------------------------------------------------------------------------------------------------

set local role authenticated;
select set_config(
    'request.jwt.claims',
    '{"sub": "22222222-2222-2222-2222-222222222222", "role": "authenticated"}',
    true
);

select throws_ok(
    $$ select public.claim_trial('22222222-2222-2222-2222-222222222222', 'email-z', 'device-z') $$,
    '42501',
    null,
    'The app cannot claim a trial directly'
);
select throws_ok(
    $$ select public.compute_entitlements('11111111-1111-1111-1111-111111111111') $$,
    '42501',
    null,
    'The app cannot compute entitlements directly'
);

reset role;

select * from finish();
rollback;
