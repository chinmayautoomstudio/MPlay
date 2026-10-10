-- Admin function tests for M5. Run with `supabase test db`.
begin;

create extension if not exists pgtap with schema extensions;

select plan(31);

insert into auth.users (id, email, raw_user_meta_data)
values
    ('aaaaaaaa-0000-0000-0000-000000000001', 'boss@example.com', '{"name": "Boss"}'),
    ('aaaaaaaa-0000-0000-0000-000000000002', 'user@example.com', '{"name": "Plain User"}'),
    ('aaaaaaaa-0000-0000-0000-000000000003', 'gone@example.com', '{"name": "Gone"}');

-- Make Boss the only enabled Admin for these tests.
update public.profiles set role = 'user' where id not in ('aaaaaaaa-0000-0000-0000-000000000001');
update public.profiles set role = 'admin' where id = 'aaaaaaaa-0000-0000-0000-000000000001';
update public.profiles set role = 'admin', disabled = true where id = 'aaaaaaaa-0000-0000-0000-000000000003';

-- Who may call ---------------------------------------------------------------------------------------------

select throws_ok($$ select public.admin_overview('aaaaaaaa-0000-0000-0000-000000000002') $$, '42501', null,
    'A normal user is refused');
select throws_ok($$ select public.admin_overview('aaaaaaaa-0000-0000-0000-000000000003') $$, '42501', null,
    'A disabled Admin is refused');
select throws_ok($$ select public.admin_set_role('aaaaaaaa-0000-0000-0000-000000000002',
    'aaaaaaaa-0000-0000-0000-000000000002', 'admin') $$, '42501', null, 'A user cannot make themselves Admin');
select throws_ok($$ select public.admin_grant_pro('aaaaaaaa-0000-0000-0000-000000000002',
    'aaaaaaaa-0000-0000-0000-000000000002', now() + interval '1 month') $$, '42501', null,
    'A user cannot grant themselves Pro');
select ok((public.admin_overview('aaaaaaaa-0000-0000-0000-000000000001') ->> 'admins')::int >= 2,
    'An Admin sees the overview');

-- Last Admin and self --------------------------------------------------------------------------------------

select is(public.admin_set_role('aaaaaaaa-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'user')
    ->> 'error', 'last_admin', 'The last enabled Admin cannot be demoted');
select is(public.admin_set_disabled('aaaaaaaa-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', true)
    ->> 'error', 'self', 'An Admin cannot disable their own account');

-- Roles and disable ----------------------------------------------------------------------------------------

select is(public.admin_set_role('aaaaaaaa-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000002', 'admin')
    ->> 'role', 'admin', 'An Admin can promote a user');
select is(public.admin_set_role('aaaaaaaa-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'user')
    ->> 'role', 'user', 'With a second Admin, an Admin can step down');
select throws_ok($$ select public.admin_overview('aaaaaaaa-0000-0000-0000-000000000001') $$, '42501', null,
    'A demoted Admin loses access at once');
select is(public.admin_set_role('aaaaaaaa-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001', 'admin')
    ->> 'role', 'admin', 'The new Admin can restore the first');
select is(public.admin_set_disabled('aaaaaaaa-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000002', true)
    ->> 'disabled', 'true', 'An Admin can disable another account');
select is(public.admin_set_disabled('aaaaaaaa-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000002', false)
    ->> 'disabled', 'false', 'and enable it again');

-- Invites --------------------------------------------------------------------------------------------------

select is(public.admin_add_admin('aaaaaaaa-0000-0000-0000-000000000001', ' New.Admin@Example.com ') ->> 'result',
    'invited', 'An unknown email is saved as an invite');
select is((select count(*)::int from public.admin_invites where email = 'new.admin@example.com'), 1,
    'The invite is stored lowercased');
insert into auth.users (id, email, raw_user_meta_data)
values ('aaaaaaaa-0000-0000-0000-000000000004', 'new.admin@example.com', '{"name": "New"}');
select is((select role::text from public.profiles where id = 'aaaaaaaa-0000-0000-0000-000000000004'), 'admin',
    'The invited email is Admin on first sign-in');
select is((select count(*)::int from public.admin_invites), 0, 'The invite is used up');
select is(public.admin_add_admin('aaaaaaaa-0000-0000-0000-000000000001', 'not-an-email') ->> 'error',
    'invalid_email', 'A malformed email is refused');

-- Pro grants -----------------------------------------------------------------------------------------------

select is(public.admin_grant_pro('aaaaaaaa-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000002',
    now() + interval '1 month') ->> 'result', 'ok', 'An Admin can grant Pro');
select is(public.compute_entitlements('aaaaaaaa-0000-0000-0000-000000000002') ->> 'plan', 'pro',
    'Granted Pro counts as Pro');
select is(public.admin_revoke_pro('aaaaaaaa-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000002')
    ->> 'result', 'ok', 'An Admin can remove Pro');
select is(public.compute_entitlements('aaaaaaaa-0000-0000-0000-000000000002') ->> 'plan', 'free',
    'Removed Pro is Free again');

-- Lists and audit ------------------------------------------------------------------------------------------

select ok(exists (
    select 1 from jsonb_array_elements(public.admin_list_users('aaaaaaaa-0000-0000-0000-000000000001', 'plain', 'all', 50, 0)
        -> 'users') u where u ->> 'email' = 'user@example.com'
), 'Search finds a user by name');
select is((select count(*)::int from public.admin_audit_log where created_at = now()), 9,
    'Each change wrote one audit row');

-- Activity feed --------------------------------------------------------------------------------------------

select throws_ok($$ select public.admin_activity('aaaaaaaa-0000-0000-0000-000000000003', 50) $$, '42501', null,
    'A disabled Admin cannot read the activity feed');
select is(public.admin_grant_pro('aaaaaaaa-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000002',
    now() + interval '1 month') ->> 'result', 'ok', 'Pro can be granted again');
insert into public.admin_audit_log (actor_id, action, target_user_id, details)
values (null, 'account_deleted', null, '{"role": "user", "plan": "free"}');
select ok(exists (
    select 1 from jsonb_array_elements(public.admin_activity('aaaaaaaa-0000-0000-0000-000000000001', 100) -> 'events') e
    where e ->> 'type' = 'signup' and e ->> 'email' = 'user@example.com'
), 'Sign-ups are in the feed');
select ok(exists (
    select 1 from jsonb_array_elements(public.admin_activity('aaaaaaaa-0000-0000-0000-000000000001', 100) -> 'events') e
    where e ->> 'type' = 'subscribed' and e ->> 'provider' = 'admin' and e ->> 'email' = 'user@example.com'
), 'Active subscriptions are in the feed');
select ok(exists (
    select 1 from jsonb_array_elements(public.admin_activity('aaaaaaaa-0000-0000-0000-000000000001', 100) -> 'events') e
    where e ->> 'type' = 'deleted' and e ->> 'plan' = 'free' and e ->> 'email' is null
), 'Deleted accounts are in the feed, without an email');
select is(
    (select count(*)::int from (
        select (e ->> 'at')::timestamptz as at, lag((e ->> 'at')::timestamptz) over (order by n) as prev
        from jsonb_array_elements(public.admin_activity('aaaaaaaa-0000-0000-0000-000000000001', 100) -> 'events')
            with ordinality as t(e, n)
    ) x where prev < at),
    0, 'The feed is newest first');

-- Access ---------------------------------------------------------------------------------------------------

set local role authenticated;
select throws_ok($$ select public.admin_overview('aaaaaaaa-0000-0000-0000-000000000001') $$, '42501', null,
    'The app cannot call admin functions directly');
reset role;

select * from finish();
rollback;
