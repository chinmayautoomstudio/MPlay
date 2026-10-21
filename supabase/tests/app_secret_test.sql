-- app_secret() allow-list and execute grants. Run with `supabase test db`.
begin;

create extension if not exists pgtap with schema extensions;

select plan(3);

select is(public.app_secret('not_a_secret'), null, 'Names outside the allow-list return null');

insert into auth.users (id, email, raw_user_meta_data)
values ('aaaaaaaa-0000-0000-0000-000000000001', 'secret-test@example.com', '{"name": "Secret"}');

set local role authenticated;
select set_config(
    'request.jwt.claims',
    '{"sub": "aaaaaaaa-0000-0000-0000-000000000001", "role": "authenticated"}',
    true
);

select throws_ok(
    $$ select public.app_secret('trial_hash_pepper') $$,
    '42501',
    null,
    'The app cannot read app secrets'
);

reset role;

select ok(
    has_function_privilege('service_role', 'public.app_secret(text)', 'execute'),
    'The service role can read app secrets'
);

select * from finish();
rollback;
