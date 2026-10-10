-- PayU billing flow tests: verification and activation, idempotency, renewals, failures, cancellation, expiry,
-- refunds, disputes, webhooks and the Admin billing views. Run with `supabase test db` or
-- `supabase/tests/run_live.ps1`. Everything is rolled back.
begin;

create extension if not exists pgtap with schema extensions;

select plan(54);

insert into auth.users (id, email, raw_user_meta_data)
values
    ('dddddddd-0000-0000-0000-000000000001', 'auto@example.com', '{"name": "Auto"}'),
    ('dddddddd-0000-0000-0000-000000000002', 'tamper@example.com', '{"name": "Tamper"}'),
    ('dddddddd-0000-0000-0000-000000000003', 'slow@example.com', '{"name": "Slow"}'),
    ('dddddddd-0000-0000-0000-000000000004', 'nomandate@example.com', '{"name": "No Mandate"}'),
    ('dddddddd-0000-0000-0000-000000000005', 'manual@example.com', '{"name": "Manual"}'),
    ('dddddddd-0000-0000-0000-000000000006', 'billingadmin@example.com', '{"name": "Billing Admin"}');
update public.profiles set phone = '98765000' || right(id::text, 2) where id::text like 'dddddddd-%';
update public.profiles set role = 'admin' where id = 'dddddddd-0000-0000-0000-000000000006';

create temp table c (who text primary key, r jsonb);
create function pg_temp.txn(p_who text) returns text language sql as $$ select r ->> 'txnId' from c where who = p_who $$;
create function pg_temp.sub(p_user text) returns public.subscriptions language sql as $$
    select * from public.subscriptions where user_id = p_user::uuid and provider = 'payu' order by created_at desc limit 1
$$;
create function pg_temp.ok_result(p_txn text, p_ref text, p_mandate text, p_method text) returns jsonb language sql as $$
    select jsonb_build_object('status', 'success', 'txnId', p_txn, 'payuRef', p_ref, 'amountPaise', 9900,
                              'method', p_method, 'mandateRef', p_mandate)
$$;

-- First payment with autopay (SV1-SV6) ----------------------------------------------------------------------

insert into c values ('auto', public.begin_checkout('dddddddd-0000-0000-0000-000000000001', null, 'autopay'));
select public.attach_payment_link(pg_temp.txn('auto'), 'https://pay.example/auto', 'L-auto');

select is(public.apply_payment_result(pg_temp.txn('auto'), pg_temp.ok_result(pg_temp.txn('auto'), 'P-auto', 'M-auto', 'UPI'))
    ->> 'result', 'applied', 'A verified payment is applied');
select is((pg_temp.sub('dddddddd-0000-0000-0000-000000000001')).status, 'active', 'The subscription is active');
select is((pg_temp.sub('dddddddd-0000-0000-0000-000000000001')).autopay_status, 'on', 'Autopay is on with a mandate');
select is((pg_temp.sub('dddddddd-0000-0000-0000-000000000001')).mandate_ref, 'M-auto', 'The mandate reference is stored');
select is((pg_temp.sub('dddddddd-0000-0000-0000-000000000001')).expires_at,
    public.next_billing_date(extract(day from now() at time zone 'Asia/Kolkata')::integer, now()),
    'The paid month ends on the anchor day next month');
select is(public.compute_entitlements('dddddddd-0000-0000-0000-000000000001') ->> 'plan', 'pro', 'The user is Pro');
select is(public.apply_payment_result(pg_temp.txn('auto'), pg_temp.ok_result(pg_temp.txn('auto'), 'P-auto', 'M-auto', 'UPI'))
    ->> 'result', 'unchanged', 'The same payment applied again changes nothing');
select is((select count(*)::int from public.payment_events
           where txn_id = pg_temp.txn('auto') and event_type = 'subscription_activated'), 1,
    'One activation, however often the result arrives');
select is(public.apply_payment_result(pg_temp.txn('auto'), jsonb_build_object('status', 'failure', 'txnId', pg_temp.txn('auto')))
    ->> 'status', 'success', 'A later failure cannot undo a settled payment');

-- Pre-debit notice and renewal (RN1-RN3, RN8) ---------------------------------------------------------------

update public.subscriptions set next_billing_at = now() + interval '1 day', expires_at = now() + interval '1 day'
where id = (pg_temp.sub('dddddddd-0000-0000-0000-000000000001')).id;
insert into c values ('predebit', public.claim_pre_debits(50));
select ok((select r from c where who = 'predebit') @> jsonb_build_array(jsonb_build_object(
    'subscriptionId', (pg_temp.sub('dddddddd-0000-0000-0000-000000000001')).id)),
    'A debit within 2 days gets a pre-debit notice');
select ok(not public.claim_pre_debits(50) @> jsonb_build_array(jsonb_build_object(
    'subscriptionId', (pg_temp.sub('dddddddd-0000-0000-0000-000000000001')).id)),
    'The notice is claimed once');

update public.subscriptions
set expires_at = now() - interval '1 hour', next_billing_at = now() - interval '1 hour',
    pre_debit_for = now() - interval '1 hour', pre_debit_sent_at = now() - interval '25 hours'
where id = (pg_temp.sub('dddddddd-0000-0000-0000-000000000001')).id;
insert into c select 'renew1', e from jsonb_array_elements(public.claim_renewals(50)) e
where e ->> 'subscriptionId' = (pg_temp.sub('dddddddd-0000-0000-0000-000000000001')).id::text;
select ok(pg_temp.txn('renew1') is not null, 'A due renewal creates one debit');
select ok(not public.claim_renewals(50) @> jsonb_build_array(jsonb_build_object(
    'subscriptionId', (pg_temp.sub('dddddddd-0000-0000-0000-000000000001')).id)),
    'Running the renewal job again creates no second debit');
select is(public.apply_payment_result(pg_temp.txn('renew1'), pg_temp.ok_result(pg_temp.txn('renew1'), 'P-renew1', null, 'UPI'))
    ->> 'kind', 'renewal', 'The renewal debit is applied');
select is((pg_temp.sub('dddddddd-0000-0000-0000-000000000001')).expires_at,
    public.next_billing_date((pg_temp.sub('dddddddd-0000-0000-0000-000000000001')).billing_anchor_day, now() - interval '1 hour'),
    'A renewal extends from the old period end on the anchor day');
select is((pg_temp.sub('dddddddd-0000-0000-0000-000000000001')).mandate_ref, 'M-auto', 'The mandate is kept');

-- Failed renewal, grace, cancellation and expiry (RN4, RN5, CN1-CN3) --------------------------------------------

update public.subscriptions
set expires_at = now() - interval '2 hours', next_billing_at = now() - interval '2 hours',
    pre_debit_for = now() - interval '2 hours', pre_debit_sent_at = now() - interval '25 hours'
where id = (pg_temp.sub('dddddddd-0000-0000-0000-000000000001')).id;
insert into c select 'renew2', e from jsonb_array_elements(public.claim_renewals(50)) e
where e ->> 'subscriptionId' = (pg_temp.sub('dddddddd-0000-0000-0000-000000000001')).id::text;
select public.fail_renewal(pg_temp.txn('renew2'), 'insufficient funds');
select is((pg_temp.sub('dddddddd-0000-0000-0000-000000000001')).status, 'past_due', 'A failed renewal is past_due');
select is((pg_temp.sub('dddddddd-0000-0000-0000-000000000001')).grace_end, now() + interval '3 days',
    'with a 3-day grace period');
select is(public.plan_of('dddddddd-0000-0000-0000-000000000001'), 'pro', 'Pro continues during the grace period');

insert into c values ('cancel', public.begin_cancel('dddddddd-0000-0000-0000-000000000001'));
select is((select r ->> 'mandateRef' from c where who = 'cancel'), 'M-auto',
    'Cancelling asks for the mandate to be cancelled with PayU');
select ok((pg_temp.sub('dddddddd-0000-0000-0000-000000000001')).cancel_at_period_end,
    'Renewals stop as soon as the user cancels');
select is(public.compute_entitlements('dddddddd-0000-0000-0000-000000000001') -> 'billing' ->> 'cancelPending', 'true',
    'The app sees the cancellation in progress until PayU confirms');
select public.confirm_mandate_cancelled((pg_temp.sub('dddddddd-0000-0000-0000-000000000001')).id, false);
select is((pg_temp.sub('dddddddd-0000-0000-0000-000000000001')).status, 'cancelled', 'Confirmed: cancelled');
select is((pg_temp.sub('dddddddd-0000-0000-0000-000000000001')).autopay_status, 'off', 'Autopay is off');
select is(public.plan_of('dddddddd-0000-0000-0000-000000000001'), 'pro', 'Pro stays until the end');

update public.subscriptions set expires_at = now() - interval '1 minute'
where id = (pg_temp.sub('dddddddd-0000-0000-0000-000000000001')).id;
select public.expire_subscriptions();
select is((pg_temp.sub('dddddddd-0000-0000-0000-000000000001')).status, 'expired', 'The expiry job ends it');
select is(public.plan_of('dddddddd-0000-0000-0000-000000000001'), 'free', 'and the user is Free');
select ok(public.claim_notices(200) @> jsonb_build_array(jsonb_build_object(
    'type', 'expired', 'userId', 'dddddddd-0000-0000-0000-000000000001')), 'An expiry email is due');

-- Tampering and unresolved attempts (SV3, CK9) ----------------------------------------------------------------

insert into c values ('tamper', public.begin_checkout('dddddddd-0000-0000-0000-000000000002', null, 'autopay'));
select public.attach_payment_link(pg_temp.txn('tamper'), 'https://pay.example/tamper', null);
select is(public.apply_payment_result(pg_temp.txn('tamper'),
    jsonb_build_object('status', 'success', 'txnId', pg_temp.txn('tamper'), 'payuRef', 'P-t', 'amountPaise', 100))
    ->> 'error', 'mismatch', 'A wrong amount is rejected');
select is(public.compute_entitlements('dddddddd-0000-0000-0000-000000000002') ->> 'plan', 'free',
    'and gives no Pro');
select ok(exists (select 1 from public.payment_events where txn_id = pg_temp.txn('tamper')
                  and event_type = 'verification_mismatch'), 'The mismatch is logged');

insert into c values ('tamper2', public.begin_checkout('dddddddd-0000-0000-0000-000000000002', null, 'autopay'));
select public.attach_payment_link(pg_temp.txn('tamper2'), 'https://pay.example/tamper2', null);
update public.payments set link_expires_at = now() - interval '1 hour' where txn_id = pg_temp.txn('tamper2');
select is(public.apply_payment_result(pg_temp.txn('tamper2'), jsonb_build_object('status', 'not_found', 'txnId', pg_temp.txn('tamper2')))
    ->> 'status', 'failed', 'An expired link PayU never saw is closed');
select is((pg_temp.sub('dddddddd-0000-0000-0000-000000000002')).status, 'failed', 'and its subscription fails');

insert into c values ('slow', public.begin_checkout('dddddddd-0000-0000-0000-000000000003', null, 'autopay'));
select public.attach_payment_link(pg_temp.txn('slow'), 'https://pay.example/slow', null);
select public.apply_payment_result(pg_temp.txn('slow'), jsonb_build_object('status', 'pending', 'txnId', pg_temp.txn('slow'), 'method', 'ENACH'));
select is((select resolve_until from public.payments where txn_id = pg_temp.txn('slow')), now() + interval '2 hours 3 days',
    'eNACH gets 3 days after the link expires before it is failed');
select is(public.begin_checkout('dddddddd-0000-0000-0000-000000000003', null, 'autopay') ->> 'error', 'payment_in_progress',
    'A payment still being resolved blocks a new attempt');
update public.payments set resolve_until = now() - interval '1 minute' where txn_id = pg_temp.txn('slow');
select public.apply_payment_result(pg_temp.txn('slow'), jsonb_build_object('status', 'pending', 'txnId', pg_temp.txn('slow')));
select is((select failure_reason from public.payments where txn_id = pg_temp.txn('slow')), 'timeout',
    'After the deadline it is failed');

-- Without a mandate, manual mode (SV4, 5.4) -----------------------------------------------------------------

insert into c values ('nomandate', public.begin_checkout('dddddddd-0000-0000-0000-000000000004', null, 'autopay'));
select public.attach_payment_link(pg_temp.txn('nomandate'), 'https://pay.example/nm', null);
select public.apply_payment_result(pg_temp.txn('nomandate'), pg_temp.ok_result(pg_temp.txn('nomandate'), 'P-nm', null, 'CC'));
select is((pg_temp.sub('dddddddd-0000-0000-0000-000000000004')).autopay_status, 'not_set',
    'A paid month without a mandate has autopay not_set');
select ok((pg_temp.sub('dddddddd-0000-0000-0000-000000000004')).cancel_at_period_end, 'and ends at the period end');
select is(public.begin_cancel('dddddddd-0000-0000-0000-000000000004') ->> 'result', 'cancelled',
    'Without a mandate cancelling is immediate');

insert into c values ('manual', public.begin_checkout('dddddddd-0000-0000-0000-000000000005', null, 'manual'));
select public.attach_payment_link(pg_temp.txn('manual'), 'https://pay.example/manual', null);
select public.apply_payment_result(pg_temp.txn('manual'), pg_temp.ok_result(pg_temp.txn('manual'), 'P-manual', null, 'UPI'));
select is((pg_temp.sub('dddddddd-0000-0000-0000-000000000005')).autopay_status, 'off', 'Manual mode has autopay off');

-- Refunds and disputes (RF1-RF6) ----------------------------------------------------------------------------

select is(public.apply_dispute(pg_temp.txn('nomandate'), 'open') ->> 'result', 'applied', 'A dispute is recorded');
select is((select status from public.payments where txn_id = pg_temp.txn('nomandate')), 'disputed', 'as disputed');
select public.apply_dispute(pg_temp.txn('nomandate'), 'won');
select is((select status from public.payments where txn_id = pg_temp.txn('nomandate')), 'success',
    'A won dispute restores the payment');

select is(public.apply_refund_result(pg_temp.txn('manual'), 'success', 9900) ->> 'expired', 'true',
    'A full refund of the current month ends Pro');
select is(public.plan_of('dddddddd-0000-0000-0000-000000000005'), 'free', 'The refunded user is Free');
select is(public.apply_refund_result(pg_temp.txn('manual'), 'success', 9900) ->> 'result', 'unchanged',
    'The same refund applied again changes nothing');

-- Webhook log (SV7) -----------------------------------------------------------------------------------------

insert into c values ('wh1', public.log_webhook('test-key-1', '{"txnid": "x"}'));
select public.finish_webhook(((select r from c where who = 'wh1') ->> 'id')::bigint, 'processed', null);
select is(public.log_webhook('test-key-1', '{"txnid": "x"}') ->> 'duplicate', 'true', 'A repeated webhook is a duplicate');
insert into c values ('wh2', public.log_webhook('test-key-2', '{"txnid": "y"}'));
select public.finish_webhook(((select r from c where who = 'wh2') ->> 'id')::bigint, 'failed', 'boom');
update public.webhook_log set next_attempt_at = now() - interval '1 minute' where event_key = 'test-key-2';
select ok(public.claim_webhooks(100) @> jsonb_build_array(jsonb_build_object('id', ((select r from c where who = 'wh2') ->> 'id')::bigint)),
    'A failed webhook is retried by the job');

-- Admin (AD1-AD4) -------------------------------------------------------------------------------------------

select throws_ok($$ select public.admin_list_payments('dddddddd-0000-0000-0000-000000000002', '', 'all', 10, 0) $$,
    '42501', null, 'A normal user cannot list payments');
select is((public.admin_list_payments('dddddddd-0000-0000-0000-000000000006', 'manual@example', 'refunded', 10, 0)
           -> 'payments' -> 0 ->> 'txnId'), pg_temp.txn('manual'), 'An Admin filters payments by user and status');
select is(public.admin_billing_action('dddddddd-0000-0000-0000-000000000006', 'refund', null, pg_temp.txn('tamper'))
    ->> 'error', 'not_refundable', 'Only a paid payment can be refunded');
select ok(jsonb_array_length(public.admin_user_detail('dddddddd-0000-0000-0000-000000000006',
    'dddddddd-0000-0000-0000-000000000001') -> 'payments') = 3, 'The user detail lists their payments');

-- Account deletion keeps payment records (CN6) --------------------------------------------------------------

select is(public.delete_account('dddddddd-0000-0000-0000-000000000005') ->> 'result', 'deleted', 'The account is deleted');
select ok(exists (select 1 from public.payments where txn_id = pg_temp.txn('manual') and user_id is null),
    'and the payment record is kept without the user');

select * from finish();
rollback;
