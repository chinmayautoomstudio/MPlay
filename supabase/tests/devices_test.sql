-- Two-device sign-in limit. Run with `supabase test db` or `run_live.ps1`. Rolled back.
begin;

create extension if not exists pgtap with schema extensions;

select plan(16);

insert into auth.users (id, email, raw_user_meta_data)
values
    ('dddddddd-0000-0000-0000-000000000001', 'devices@example.com', '{"name": "Devices"}'),
    ('dddddddd-0000-0000-0000-000000000002', 'gone@example.com', '{"name": "Gone"}');

update public.profiles set disabled = true where id = 'dddddddd-0000-0000-0000-000000000002';

-- First two phones register; a third is refused until one is replaced.
select is(
    public.register_device(
        'dddddddd-0000-0000-0000-000000000001',
        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        'Pixel 8'
    ) ->> 'result',
    'registered',
    'The first phone registers'
);
select is(
    public.register_device(
        'dddddddd-0000-0000-0000-000000000001',
        'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
        'Pixel 9'
    ) ->> 'result',
    'registered',
    'A second phone registers'
);
select is(
    public.register_device(
        'dddddddd-0000-0000-0000-000000000001',
        'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
        'Pixel 10'
    ) ->> 'result',
    'limit_reached',
    'A third phone is refused'
);
select is(
    jsonb_array_length(public.device_list('dddddddd-0000-0000-0000-000000000001', null)),
    2,
    'The account still has two devices after the refused third'
);

-- Signing in again on a phone that is already registered does not consume a new slot.
select is(
    public.register_device(
        'dddddddd-0000-0000-0000-000000000001',
        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        'Pixel 8a'
    ) ->> 'result',
    'registered',
    'Re-registering the same phone succeeds'
);
select is(
    (select device_name from public.user_devices
     where user_id = 'dddddddd-0000-0000-0000-000000000001'
       and device_id = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'),
    'Pixel 8a',
    'Re-registering updates the device name'
);

-- Replacing one of the two lets the third phone in.
select is(
    public.register_device(
        'dddddddd-0000-0000-0000-000000000001',
        'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
        'Pixel 10',
        null,
        'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'
    ) ->> 'result',
    'registered',
    'Replacing one of the two phones registers the third'
);
select is(
    (select count(*)::int from public.user_devices where user_id = 'dddddddd-0000-0000-0000-000000000001'),
    2,
    'The account is still at two devices after a replacement'
);
select ok(
    not exists (
        select 1 from public.user_devices
        where user_id = 'dddddddd-0000-0000-0000-000000000001'
          and device_id = 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'
    ),
    'The replaced phone is gone'
);

-- verify_device: current phone is ok; a logged-out phone is revoked; a legacy account auto-registers.
select is(
    public.verify_device(
        'dddddddd-0000-0000-0000-000000000001',
        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
    ) ->> 'status',
    'ok',
    'A registered phone verifies'
);
select is(
    public.verify_device(
        'dddddddd-0000-0000-0000-000000000001',
        'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'
    ) ->> 'status',
    'revoked',
    'A phone that was logged out remotely is revoked'
);
select is(
    public.verify_device(
        'dddddddd-0000-0000-0000-000000000002',
        'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd'
    ) ->> 'status',
    'disabled',
    'A disabled account cannot verify a device'
);

insert into auth.users (id, email, raw_user_meta_data)
values ('dddddddd-0000-0000-0000-000000000003', 'legacy@example.com', '{"name": "Legacy"}');

select is(
    public.verify_device(
        'dddddddd-0000-0000-0000-000000000003',
        'eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee',
        'Old phone'
    ) ->> 'status',
    'ok',
    'A signed-in install with no device row is registered as the first phone'
);
select is(
    (select count(*)::int from public.user_devices where user_id = 'dddddddd-0000-0000-0000-000000000003'),
    1,
    'Legacy verify created one device'
);

select is(
    public.remove_device(
        'dddddddd-0000-0000-0000-000000000001',
        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
    ) ->> 'result',
    'removed',
    'A phone can be removed'
);
select is(
    (select count(*)::int from public.user_devices where user_id = 'dddddddd-0000-0000-0000-000000000001'),
    1,
    'Removing a phone frees a slot'
);

select * from finish();
rollback;
