-- PayU billing tests: constraints, the shared Pro rule, billing dates and checkout. Run with `supabase test db` or
-- `supabase/tests/run_live.ps1`. Everything is rolled back.
begin;

create extension if not exists pgtap with schema extensions;

select plan(31);

insert into auth.users (id, email, raw_user_meta_data)
values
    ('cccccccc-0000-0000-0000-000000000001', 'buyer@example.com', '{"name": "Buyer"}'),
    ('cccccccc-0000-0000-0000-000000000002', 'late@example.com', '{"name": "Late"}'),
    ('cccccccc-0000-0000-0000-000000000003', 'noauto@example.com', '{"name": "No Auto"}'),
    ('cccccccc-0000-0000-0000-000000000004', 'spam@example.com', '{"name": "Spam"}'),
    ('cccccccc-0000-0000-0000-000000000005', 'off@example.com', '{"name": "Off"}'),
    ('cccccccc-0000-0000-0000-000000000006', 'rules@example.com', '{"name": "Rules"}');

-- Billing dates (5.3) --------------------------------------------------------------------------------------

select is(public.next_billing_date(31, '2027-01-31 10:00+05:30'), '2027-02-28 10:00+05:30'::timestamptz,
    '31 Jan renews on 28 Feb');
select is(public.next_billing_date(31, '2027-02-28 10:00+05:30'), '2027-03-31 10:00+05:30'::timestamptz,
    'then on 31 Mar, from the anchor and not the previous date');
select is(public.next_billing_date(31, '2027-03-31 10:00+05:30'), '2027-04-30 10:00+05:30'::timestamptz,
    'then on 30 Apr');
select is(public.next_billing_date(31, '2028-01-31 10:00+05:30'), '2028-02-29 10:00+05:30'::timestamptz,
    'A leap year renews on 29 Feb');
select is(public.next_billing_date(15, '2026-12-15 23:30+05:30'), '2027-01-15 23:30+05:30'::timestamptz,
    'The year rolls over in India time');

-- Constraints ----------------------------------------------------------------------------------------------

select throws_ok(
    $$ insert into public.subscriptions (user_id, status, provider) values
       ('cccccccc-0000-0000-0000-000000000006', 'halted', 'payu') $$,
    '23514', null, 'halted is no longer a status');
select throws_ok(
    $$ insert into public.subscriptions (user_id, status, provider) values
       ('cccccccc-0000-0000-0000-000000000006', 'active', 'razorpay') $$,
    '23514', null, 'Razorpay is no longer a provider');
insert into public.subscriptions (user_id, status, provider, expires_at, autopay_status)
values ('cccccccc-0000-0000-0000-000000000006', 'active', 'payu', now() - interval '12 hours', 'on');
select throws_ok(
    $$ insert into public.subscriptions (user_id, status, provider) values
       ('cccccccc-0000-0000-0000-000000000006', 'pending', 'payu') $$,
    '23505', null, 'A user has at most one live PayU subscription');
select lives_ok(
    $$ insert into public.subscriptions (user_id, status, provider, provider_ref, expires_at) values
       ('cccccccc-0000-0000-0000-000000000006', 'expired', 'admin', 'admin:x', now() - interval '1 day') $$,
    'An Admin grant row sits alongside a PayU subscription');
select throws_ok(
    $$ update public.profiles set phone = '12345' where id = 'cccccccc-0000-0000-0000-000000000006' $$,
    '23514', null, 'Only a 10-digit Indian mobile number is stored');

-- The Pro rule (2.1) ---------------------------------------------------------------------------------------

select is(public.compute_entitlements('cccccccc-0000-0000-0000-000000000006') ->> 'plan', 'pro',
    'Autopay keeps Pro for a day after the period end while the renewal runs');
update public.subscriptions set autopay_status = 'not_set'
where user_id = 'cccccccc-0000-0000-0000-000000000006' and provider = 'payu';
select is(public.compute_entitlements('cccccccc-0000-0000-0000-000000000006') ->> 'plan', 'free',
    'Without autopay Pro ends at the period end');
select is(public.plan_of('cccccccc-0000-0000-0000-000000000006'), 'free', 'plan_of uses the same rule');

insert into public.subscriptions (user_id, status, provider, expires_at, grace_end, autopay_status)
values ('cccccccc-0000-0000-0000-000000000002', 'past_due', 'payu', now() - interval '1 day',
        now() + interval '2 days', 'on');
select is(public.compute_entitlements('cccccccc-0000-0000-0000-000000000002') ->> 'plan', 'pro',
    'past_due keeps Pro during the grace period');
select is(public.plan_of('cccccccc-0000-0000-0000-000000000002'), 'pro', 'plan_of agrees during the grace period');

-- Checkout (CK2, CK3, CK13) --------------------------------------------------------------------------------

select is(public.begin_checkout('cccccccc-0000-0000-0000-000000000001', null, 'autopay') ->> 'error', 'phone_required',
    'Checkout asks for a mobile number first');
select is(public.begin_checkout('cccccccc-0000-0000-0000-000000000001', '5876543210', 'autopay') ->> 'error',
    'invalid_phone', 'An invalid mobile number is refused');

create temp table first_try as
select public.begin_checkout('cccccccc-0000-0000-0000-000000000001', '9876543210', 'autopay') as r;
select is((select r ->> 'kind' from first_try), 'first', 'A first checkout creates an attempt');
select is((select phone from public.profiles where id = 'cccccccc-0000-0000-0000-000000000001'), '9876543210',
    'The mobile number is saved for renewals');
select is(
    (select amount_paise from public.payments where txn_id = (select r ->> 'txnId' from first_try)), 9900,
    'The amount is fixed on the server');
select is(public.begin_checkout('cccccccc-0000-0000-0000-000000000001', null, 'autopay') ->> 'error',
    'payment_in_progress', 'An attempt without a link yet blocks a second one');

select public.attach_payment_link((select r ->> 'txnId' from first_try), 'https://pay.example/abc', 'L1');
select is(public.begin_checkout('cccccccc-0000-0000-0000-000000000001', null, 'autopay') ->> 'url',
    'https://pay.example/abc', 'A live link is handed out again instead of a second one');
select is(
    public.compute_entitlements('cccccccc-0000-0000-0000-000000000001') -> 'pendingPayment' ->> 'txnId',
    (select r ->> 'txnId' from first_try), 'Entitlements report the pending payment');

select public.fail_checkout((select r ->> 'txnId' from first_try), 'link_error');
select is(
    (select status from public.subscriptions where user_id = 'cccccccc-0000-0000-0000-000000000001'),
    'failed', 'A failed first attempt fails its subscription');

update public.profiles set disabled = true, phone = '9876543212' where id = 'cccccccc-0000-0000-0000-000000000005';
select is(public.begin_checkout('cccccccc-0000-0000-0000-000000000005', null, 'autopay') ->> 'error',
    'account_disabled', 'A disabled account cannot check out');

select is(public.begin_checkout('cccccccc-0000-0000-0000-000000000002', '9876543211', 'autopay') ->> 'error',
    'mandate_active', 'Fix payment needs the old mandate cancelled first (5.5)');

insert into public.subscriptions (user_id, status, provider, started_at, expires_at, autopay_status,
                                  billing_anchor_day)
values ('cccccccc-0000-0000-0000-000000000003', 'active', 'payu', now() - interval '20 days',
        now() + interval '10 days', 'on', 5);
update public.profiles set phone = '9876543213' where id = 'cccccccc-0000-0000-0000-000000000003';
select is(public.begin_checkout('cccccccc-0000-0000-0000-000000000003', null, 'autopay') ->> 'error',
    'already_subscribed', 'An active subscription with autopay cannot buy again');
update public.subscriptions set autopay_status = 'off' where user_id = 'cccccccc-0000-0000-0000-000000000003';
select is(public.begin_checkout('cccccccc-0000-0000-0000-000000000003', null, 'manual') ->> 'error',
    'already_subscribed', 'Manual renewal opens only in the last week');
create temp table setup_autopay as
select public.begin_checkout('cccccccc-0000-0000-0000-000000000003', null, 'autopay') as r;
select is((select r ->> 'kind' from setup_autopay), 'replace', 'Setting up autopay replaces the payment setup');
select is((select (r ->> 'periodEnd')::timestamptz from setup_autopay),
    (select public.next_billing_date(5, expires_at) from public.subscriptions
     where user_id = 'cccccccc-0000-0000-0000-000000000003'),
    'The new month is added after the current paid period, on the anchor day');

update public.profiles set phone = '9876543214' where id = 'cccccccc-0000-0000-0000-000000000004';
insert into public.payments (txn_id, user_id, kind, status, amount_paise)
select 'SPAM' || g, 'cccccccc-0000-0000-0000-000000000004', 'first', 'failed', 9900 from generate_series(1, 5) g;
select is(public.begin_checkout('cccccccc-0000-0000-0000-000000000004', null, 'autopay') ->> 'error', 'rate_limited',
    'At most 5 checkout attempts an hour');

select * from finish();
rollback;
