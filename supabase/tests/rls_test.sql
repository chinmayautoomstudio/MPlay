-- Row Level Security and column-permission tests for the M1 foundation. Run with `supabase test db`.
begin;

create extension if not exists pgtap with schema extensions;

select plan(17);

-- Two users; the trigger creates their profiles from the Google metadata.
insert into auth.users (id, email, raw_user_meta_data)
values
    ('11111111-1111-1111-1111-111111111111', 'alice@example.com',
     '{"full_name": "Alice", "avatar_url": "https://example.com/a.png"}'),
    ('22222222-2222-2222-2222-222222222222', 'bob@example.com', '{"name": "Bob"}');

select is(
    (select display_name from public.profiles where id = '11111111-1111-1111-1111-111111111111'),
    'Alice',
    'Trigger fills display_name from full_name'
);
select is(
    (select display_name from public.profiles where id = '22222222-2222-2222-2222-222222222222'),
    'Bob',
    'Trigger falls back to name'
);
select is(
    (select role::text from public.profiles where id = '11111111-1111-1111-1111-111111111111'),
    'user',
    'New accounts are always Users'
);

-- Act as Alice.
set local role authenticated;
select set_config(
    'request.jwt.claims',
    '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}',
    true
);

select results_eq(
    'select id from public.profiles',
    $$ values ('11111111-1111-1111-1111-111111111111'::uuid) $$,
    'A user sees only their own profile'
);

select lives_ok(
    $$ update public.profiles set display_name = 'Alice B' where id = '11111111-1111-1111-1111-111111111111' $$,
    'A user can rename themselves'
);

select throws_ok(
    $$ update public.profiles set role = 'admin' where id = '11111111-1111-1111-1111-111111111111' $$,
    '42501',
    null,
    'A user cannot change their own role'
);

select throws_ok(
    $$ update public.profiles set disabled = true where id = '11111111-1111-1111-1111-111111111111' $$,
    '42501',
    null,
    'A user cannot change their own disabled flag'
);

update public.profiles set display_name = 'Hacked' where id = '22222222-2222-2222-2222-222222222222';

select is(public.is_admin(), false, 'A normal user is not an Admin');

select throws_ok(
    $$ insert into public.subscriptions (user_id, status, provider, provider_ref)
       values ('11111111-1111-1111-1111-111111111111', 'active', 'razorpay', 'sub_x') $$,
    '42501',
    null,
    'A user cannot write subscriptions'
);

select throws_ok(
    $$ insert into public.trials (user_id, started_at, ends_at)
       values ('11111111-1111-1111-1111-111111111111', now(), now() + interval '30 days') $$,
    '42501',
    null,
    'A user cannot write trials'
);

select throws_ok(
    $$ insert into public.ai_usage (user_id, job_ref, status, week_start)
       values ('11111111-1111-1111-1111-111111111111', 'job-1', 'completed', current_date) $$,
    '42501',
    null,
    'A user cannot write AI usage'
);

select throws_ok(
    'select * from public.trial_claims',
    '42501',
    null,
    'A user cannot read trial claims'
);

select throws_ok(
    'select * from public.payment_events',
    '42501',
    null,
    'A user cannot read payment events'
);

select throws_ok(
    'select * from public.admin_audit_log',
    '42501',
    null,
    'A user cannot read the admin audit log'
);

-- Signed-out (anon) requests get nothing.
reset role;
set local role anon;
select throws_ok(
    'select * from public.profiles',
    '42501',
    null,
    'Anonymous requests cannot read profiles'
);

-- Back to postgres to check what actually changed.
reset role;

select is(
    (select display_name from public.profiles where id = '22222222-2222-2222-2222-222222222222'),
    'Bob',
    'A user cannot rename someone else'
);

select is(
    (select display_name from public.profiles where id = '11111111-1111-1111-1111-111111111111'),
    'Alice B',
    'The rename was saved'
);

select * from finish();
rollback;
