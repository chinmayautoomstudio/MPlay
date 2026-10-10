-- MP3 Studio Pro payments through PayU Payment Links (payments PRD section 2.1). Replaces the unused Razorpay and
-- Google Play setup: PayU is the only paying provider, `admin` stays for Admin grants. Adds the autopay (standing
-- instruction) fields, the payments and webhook_log tables, the profile mobile number PayU needs, one shared Pro rule
-- for compute_entitlements and plan_of, the billing-date rule and begin_checkout. Everything here runs only as the
-- service role, from the billing Edge Functions; the app can read its own subscriptions and payments.

-- Subscriptions -------------------------------------------------------------------------------------------

alter table public.subscriptions drop constraint subscriptions_status_check;
update public.subscriptions set status = 'past_due' where status = 'halted';
alter table public.subscriptions add constraint subscriptions_status_check
    check (status in ('pending', 'active', 'past_due', 'cancelled', 'expired', 'failed'));

alter table public.subscriptions drop constraint subscriptions_provider_check;
alter table public.subscriptions add constraint subscriptions_provider_check
    check (provider in ('payu', 'admin'));

-- The mandate reference only exists after the first payment, so a PayU row starts without one.
alter table public.subscriptions alter column provider_ref drop not null;

alter table public.subscriptions
    add column autopay_status text check (autopay_status in ('on', 'off', 'not_set', 'revoked')),
    add column mandate_ref text,
    add column mandate_start timestamptz,
    add column mandate_end timestamptz,
    add column billing_anchor_day smallint check (billing_anchor_day between 1 and 31),
    add column grace_end timestamptz,
    -- Set when the server asked PayU to cancel the mandate and PayU has not confirmed it yet (CN4, 5.5).
    add column mandate_cancel_requested_at timestamptz,
    add column mandate_cancelled_at timestamptz,
    -- The billing date the last pre-debit notice was for, and when it was sent (RN2).
    add column pre_debit_for timestamptz,
    add column pre_debit_sent_at timestamptz,
    add column renewal_attempts integer not null default 0;

-- One live PayU subscription per user (checkout and mandate safety). Admin grants are separate rows.
create unique index subscriptions_one_live_payu_idx on public.subscriptions (user_id)
    where provider = 'payu' and status in ('pending', 'active', 'past_due');

-- Payment events gain the transaction ID (EV1). Only PayU writes them from now on.
alter table public.payment_events drop constraint payment_events_provider_check;
alter table public.payment_events add constraint payment_events_provider_check check (provider in ('payu'));
alter table public.payment_events add column txn_id text;
create index payment_events_txn_idx on public.payment_events (txn_id);

-- Profiles: the mobile number UPI AutoPay, eNACH and pre-debit notices need (CK13). Written only by begin_checkout.
alter table public.profiles add column phone text check (phone ~ '^[6-9][0-9]{9}$');

-- Payments ------------------------------------------------------------------------------------------------

-- One row per payment attempt: the first charge, a monthly renewal debit, or a replacement (Fix payment,
-- resubscribe, set up autopay, manual renewal). txn_id is our transaction ID and the PayU invoice number.
create table public.payments (
    id uuid primary key default gen_random_uuid(),
    txn_id text not null unique check (txn_id ~ '^[A-Za-z0-9]{1,25}$'),
    user_id uuid references auth.users (id) on delete set null,
    subscription_id uuid references public.subscriptions (id) on delete set null,
    kind text not null check (kind in ('first', 'renewal', 'replace')),
    status text not null default 'created' check (status in (
        'created', 'pending', 'success', 'failed', 'cancelled', 'refunded', 'partially_refunded', 'disputed'
    )),
    -- True when the link registers a standing instruction (autopay); false for manual-renewal links.
    si boolean not null default false,
    amount_paise integer not null check (amount_paise > 0),
    currency text not null default 'INR' check (currency = 'INR'),
    link_url text,
    link_ref text,
    link_expires_at timestamptz,
    payu_ref text unique,
    mandate_ref text,
    method text,
    failure_reason text,
    period_start timestamptz,
    period_end timestamptz,
    attempt integer not null default 1,
    -- A payment PayU still reports as in progress is checked until this time before it is failed (CK9).
    resolve_until timestamptz,
    last_checked_at timestamptz,
    refunded_paise integer not null default 0 check (refunded_paise >= 0),
    refund_request_id text,
    refund_requested_at timestamptz,
    dispute_status text check (dispute_status in ('open', 'won', 'lost')),
    created_at timestamptz not null default now(),
    completed_at timestamptz,
    updated_at timestamptz not null default now()
);

create index payments_user_idx on public.payments (user_id, created_at desc);
create index payments_open_idx on public.payments (status, last_checked_at) where status in ('created', 'pending');
-- A renewal debit happens once per billing period and attempt (RN8).
create unique index payments_one_renewal_idx on public.payments (subscription_id, period_start, attempt)
    where kind = 'renewal';

-- Every incoming PayU webhook, saved before it is processed (SV7, EV3). Service role only.
create table public.webhook_log (
    id bigint generated always as identity primary key,
    received_at timestamptz not null default now(),
    event_key text,
    body jsonb not null,
    status text not null default 'received' check (status in ('received', 'processed', 'failed', 'ignored')),
    attempts integer not null default 0,
    next_attempt_at timestamptz not null default now() + interval '5 minutes',
    last_error text,
    processed_at timestamptz,
    flagged boolean not null default false
);

create index webhook_log_retry_idx on public.webhook_log (next_attempt_at) where status in ('received', 'failed');

alter table public.payments enable row level security;
alter table public.webhook_log enable row level security;
revoke all on public.payments, public.webhook_log from anon, authenticated;

grant select on public.payments to authenticated;
create policy "Users read their own payments"
    on public.payments for select to authenticated
    using (user_id = (select auth.uid()));

-- Rules ---------------------------------------------------------------------------------------------------

-- ₹99 a month, fixed on the server (SC3).
create function public.billing_price_paise()
returns integer
language sql
immutable
set search_path = ''
as $$ select 9900 $$;

-- The one Pro rule (2.1): active and paid up, cancelled before the period end, or past_due before the grace end.
-- An active subscription with autopay on keeps Pro for one more day after the period end, so the hourly renewal job
-- has time to debit before the user notices.
create function public.is_pro_sub(s public.subscriptions)
returns boolean
language sql
stable
set search_path = ''
as $$
    select s.plan = 'pro' and case s.status
        when 'active' then s.expires_at is null or s.expires_at > now()
            or (s.autopay_status = 'on' and not s.cancel_at_period_end and s.expires_at + interval '1 day' > now())
        when 'cancelled' then s.expires_at > now()
        when 'past_due' then coalesce(s.grace_end, s.expires_at) > now()
        else false
    end
$$;

-- The billing date one month after p_from (5.3): the anchor day of the next month in India time, or that month's last
-- day when it is shorter, at the same time of day. 31 Jan -> 28 Feb (29 in a leap year) -> 31 Mar -> 30 Apr.
create function public.next_billing_date(p_anchor integer, p_from timestamptz)
returns timestamptz
language sql
stable
set search_path = ''
as $$
    with local as (select p_from at time zone 'Asia/Kolkata' as ts),
    month as (select date_trunc('month', ts) + interval '1 month' as first_day, ts from local)
    select (
        first_day
        + make_interval(days => least(
            greatest(p_anchor, 1),
            extract(day from first_day + interval '1 month' - interval '1 day')::integer
        ) - 1)
        + (ts - date_trunc('day', ts))
    ) at time zone 'Asia/Kolkata'
    from month
$$;

-- Appends a payment event once per key (EV1). provider_event_id is the idempotency key.
create function public.billing_event(
    p_user uuid, p_type text, p_key text, p_txn text, p_ref text, p_amount integer, p_payload jsonb
)
returns void
language sql
security definer
set search_path = ''
as $$
    insert into public.payment_events (user_id, provider, provider_event_id, event_type, txn_id, provider_ref,
                                       amount_paise, currency, payload)
    values (p_user, 'payu', p_key, p_type, p_txn, p_ref, p_amount, case when p_amount is null then null else 'INR' end,
            p_payload)
    on conflict (provider, provider_event_id) do nothing;
$$;

-- Plan computation ----------------------------------------------------------------------------------------

create or replace function public.plan_of(p_user uuid)
returns text
language sql
stable
security definer
set search_path = ''
as $$
    select case
        when exists (select 1 from public.subscriptions s where s.user_id = p_user and public.is_pro_sub(s)) then 'pro'
        when exists (select 1 from public.trials t where t.user_id = p_user and t.ends_at > now()) then 'trial'
        else 'free'
    end;
$$;

-- The caller's plan, trial, the subscription that gives Pro, and the PayU billing state the Plans screen shows.
create or replace function public.compute_entitlements(p_user uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_profile public.profiles;
    v_trial public.trials;
    v_sub public.subscriptions;
    v_billing public.subscriptions;
    v_open public.payments;
    v_last public.payments;
    v_plan text;
begin
    select * into v_profile from public.profiles where id = p_user;
    if not found then
        return null;
    end if;

    select * into v_trial from public.trials where user_id = p_user;

    select * into v_sub from public.subscriptions s
    where s.user_id = p_user and public.is_pro_sub(s)
    order by s.expires_at desc nulls first
    limit 1;

    select * into v_billing from public.subscriptions
    where user_id = p_user and provider = 'payu'
    order by (status in ('pending', 'active', 'past_due', 'cancelled')) desc, created_at desc
    limit 1;

    select * into v_open from public.payments
    where user_id = p_user and status in ('created', 'pending')
    order by created_at desc
    limit 1;

    select * into v_last from public.payments
    where user_id = p_user and completed_at is not null and status <> 'failed' and status <> 'cancelled'
    order by completed_at desc
    limit 1;

    if v_sub.id is not null then
        v_plan := 'pro';
    elsif v_trial.user_id is not null and v_trial.ends_at > now() then
        v_plan := 'trial';
    else
        v_plan := 'free';
    end if;

    return jsonb_build_object(
        'plan', v_plan,
        'role', v_profile.role,
        'disabled', v_profile.disabled,
        'hasPhone', v_profile.phone is not null,
        'trial', case when v_trial.user_id is null then null else jsonb_build_object(
            'startedAt', v_trial.started_at,
            'endsAt', v_trial.ends_at
        ) end,
        'subscription', case when v_sub.id is null then null else jsonb_build_object(
            'status', v_sub.status,
            'provider', v_sub.provider,
            'startedAt', v_sub.started_at,
            'expiresAt', v_sub.expires_at,
            'nextBillingAt', v_sub.next_billing_at,
            'cancelAtPeriodEnd', v_sub.cancel_at_period_end,
            'autopayStatus', v_sub.autopay_status,
            'graceEnd', v_sub.grace_end
        ) end,
        'billing', case when v_billing.id is null then null else jsonb_build_object(
            'status', v_billing.status,
            'autopayStatus', v_billing.autopay_status,
            'startedAt', v_billing.started_at,
            'expiresAt', v_billing.expires_at,
            'nextBillingAt', v_billing.next_billing_at,
            'graceEnd', v_billing.grace_end,
            'mandateEnd', v_billing.mandate_end,
            'cancelAtPeriodEnd', v_billing.cancel_at_period_end,
            'cancelPending', v_billing.mandate_cancel_requested_at is not null
                and v_billing.autopay_status = 'on',
            'lastPaymentAt', v_billing.last_payment_at
        ) end,
        'pendingPayment', case when v_open.id is null then null else jsonb_build_object(
            'txnId', v_open.txn_id,
            'status', v_open.status,
            'kind', v_open.kind,
            'linkExpiresAt', v_open.link_expires_at,
            'resolveUntil', v_open.resolve_until
        ) end,
        'lastPayment', case when v_last.id is null then null else jsonb_build_object(
            'txnId', v_last.txn_id,
            'status', v_last.status,
            'amountPaise', v_last.amount_paise,
            'at', v_last.completed_at
        ) end,
        'serverTime', now()
    );
end;
$$;

-- Checkout ------------------------------------------------------------------------------------------------

-- Starts a payment attempt for the Pro plan (CK2, CK3, CK13, 5.5). p_mode is 'autopay' (standing instruction link)
-- or 'manual' (one-time link, 5.4). p_phone, when given, is validated and saved to the profile.
-- Returns {"result": "created", txnId, kind, amountPaise, linkExpiresAt, periodEnd, customer} for a new attempt,
-- {"result": "existing", txnId, url} when a live link already exists, or {"error": ...}: not_found,
-- account_disabled, invalid_phone, phone_required, payment_in_progress, rate_limited, already_subscribed, or
-- mandate_active (with subscriptionId: the old mandate must be cancelled with PayU before a new link, 5.5).
create function public.begin_checkout(p_user uuid, p_phone text, p_mode text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_profile public.profiles;
    v_open public.payments;
    v_sub public.subscriptions;
    v_kind text;
    v_txn text;
    v_start timestamptz;
    v_anchor integer;
    v_expires timestamptz := now() + interval '2 hours';
begin
    if p_mode is null or p_mode not in ('autopay', 'manual') then
        raise exception 'invalid mode %', p_mode using errcode = '22023';
    end if;
    perform pg_advisory_xact_lock(hashtextextended('billing:' || p_user::text, 0));

    select * into v_profile from public.profiles where id = p_user for update;
    if not found then
        return jsonb_build_object('error', 'not_found');
    end if;
    if v_profile.disabled then
        return jsonb_build_object('error', 'account_disabled');
    end if;
    if p_phone is not null then
        if p_phone !~ '^[6-9][0-9]{9}$' then
            return jsonb_build_object('error', 'invalid_phone');
        end if;
        update public.profiles set phone = p_phone where id = p_user;
        v_profile.phone := p_phone;
    end if;
    if v_profile.phone is null then
        return jsonb_build_object('error', 'phone_required');
    end if;

    -- A live link is handed out again; an attempt still being resolved blocks a new one (CK3, CK9).
    select * into v_open from public.payments
    where user_id = p_user and status in ('created', 'pending')
    order by created_at desc
    limit 1;
    if found then
        if v_open.status = 'created' and v_open.link_url is not null
           and v_open.link_expires_at > now() + interval '10 minutes' then
            return jsonb_build_object('result', 'existing', 'txnId', v_open.txn_id, 'url', v_open.link_url);
        end if;
        return jsonb_build_object('error', 'payment_in_progress', 'txnId', v_open.txn_id);
    end if;

    if (select count(*) from public.payments
        where user_id = p_user and kind <> 'renewal' and created_at > now() - interval '1 hour') >= 5 then
        return jsonb_build_object('error', 'rate_limited');
    end if;

    select * into v_sub from public.subscriptions
    where user_id = p_user and provider = 'payu'
      and (status in ('pending', 'active', 'past_due') or (status = 'cancelled' and expires_at > now()))
    order by (status <> 'cancelled') desc, created_at desc
    limit 1
    for update;

    if v_sub.id is null then
        v_kind := 'first';
        insert into public.subscriptions (user_id, plan, status, provider)
        values (p_user, 'pro', 'pending', 'payu')
        returning * into v_sub;
    elsif v_sub.status = 'pending' then
        v_kind := 'first';
    else
        if v_sub.autopay_status = 'on' then
            if v_sub.status = 'active' and not v_sub.cancel_at_period_end then
                return jsonb_build_object('error', 'already_subscribed');
            end if;
            return jsonb_build_object('error', 'mandate_active', 'subscriptionId', v_sub.id);
        end if;
        -- Manual renewal opens a week before the paid month ends (5.4).
        if p_mode = 'manual' and v_sub.status = 'active' and v_sub.expires_at > now() + interval '7 days' then
            return jsonb_build_object('error', 'already_subscribed');
        end if;
        v_kind := 'replace';
    end if;

    -- A paid period still running is extended; otherwise the new month starts at payment (5.5).
    if v_kind = 'replace' and v_sub.status in ('active', 'cancelled') and v_sub.expires_at > now() then
        v_start := v_sub.expires_at;
        v_anchor := coalesce(v_sub.billing_anchor_day,
                             extract(day from v_start at time zone 'Asia/Kolkata')::integer);
    else
        v_start := now();
        v_anchor := extract(day from now() at time zone 'Asia/Kolkata')::integer;
    end if;

    v_txn := 'MP' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 20));
    insert into public.payments (txn_id, user_id, subscription_id, kind, status, si, amount_paise, link_expires_at)
    values (v_txn, p_user, v_sub.id, v_kind, 'created', p_mode = 'autopay', public.billing_price_paise(), v_expires);

    perform public.billing_event(p_user, 'payment_initiated', v_txn || ':initiated', v_txn, null,
                                 public.billing_price_paise(), jsonb_build_object('kind', v_kind, 'mode', p_mode));

    return jsonb_build_object(
        'result', 'created',
        'txnId', v_txn,
        'kind', v_kind,
        'amountPaise', public.billing_price_paise(),
        'linkExpiresAt', v_expires,
        'periodEnd', public.next_billing_date(v_anchor, v_start),
        'customer', jsonb_build_object(
            'name', coalesce(nullif(btrim(v_profile.display_name), ''), split_part(v_profile.email, '@', 1)),
            'email', v_profile.email,
            'phone', v_profile.phone
        )
    );
end;
$$;

-- Stores the PayU link for a new attempt. {"result": "ok"} or {"error": "not_found"}.
create function public.attach_payment_link(p_txn text, p_url text, p_link_ref text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
    update public.payments
    set link_url = p_url, link_ref = p_link_ref, updated_at = now()
    where txn_id = p_txn and status = 'created' and link_url is null;
    if not found then
        return jsonb_build_object('error', 'not_found');
    end if;
    return jsonb_build_object('result', 'ok');
end;
$$;

-- Closes an attempt whose link could not be created. A new subscription that never got a payment becomes failed.
create function public.fail_checkout(p_txn text, p_reason text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_payment public.payments;
begin
    update public.payments
    set status = 'failed', failure_reason = left(p_reason, 200), completed_at = now(), updated_at = now()
    where txn_id = p_txn and status = 'created'
    returning * into v_payment;
    if not found then
        return jsonb_build_object('error', 'not_found');
    end if;
    update public.subscriptions set status = 'failed', updated_at = now()
    where id = v_payment.subscription_id and status = 'pending';
    perform public.billing_event(v_payment.user_id, 'payment_failed', p_txn || ':failed', p_txn, null,
                                 v_payment.amount_paise, jsonb_build_object('reason', left(p_reason, 200)));
    return jsonb_build_object('result', 'ok');
end;
$$;

-- Account deletion refuses with mandate_active while a PayU mandate is live. The delete-account Edge Function
-- cancels the mandate with PayU first (CN6), so this is the last guard, not a refusal the user normally sees.
create or replace function public.delete_account(p_user uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_profile public.profiles;
begin
    perform pg_advisory_xact_lock(hashtextextended('admin:roles', 0));

    select * into v_profile from public.profiles where id = p_user for update;
    if not found then
        return jsonb_build_object('error', 'not_found');
    end if;
    if v_profile.role = 'admin' and not v_profile.disabled and not exists (
        select 1 from public.profiles where role = 'admin' and not disabled and id <> p_user
    ) then
        return jsonb_build_object('error', 'last_admin');
    end if;
    if exists (
        select 1 from public.subscriptions
        where user_id = p_user and provider = 'payu' and autopay_status = 'on'
    ) then
        return jsonb_build_object('error', 'mandate_active');
    end if;

    perform public.admin_log(p_user, 'account_deleted', p_user,
                             jsonb_build_object('role', v_profile.role, 'plan', public.plan_of(p_user)));
    delete from auth.users where id = p_user;
    return jsonb_build_object('result', 'deleted');
end;
$$;

-- Access --------------------------------------------------------------------------------------------------

revoke execute on function public.billing_price_paise() from public, anon, authenticated;
revoke execute on function public.is_pro_sub(public.subscriptions) from public, anon, authenticated;
revoke execute on function public.next_billing_date(integer, timestamptz) from public, anon, authenticated;
revoke execute on function public.billing_event(uuid, text, text, text, text, integer, jsonb)
    from public, anon, authenticated;
revoke execute on function public.begin_checkout(uuid, text, text) from public, anon, authenticated;
revoke execute on function public.attach_payment_link(text, text, text) from public, anon, authenticated;
revoke execute on function public.fail_checkout(text, text) from public, anon, authenticated;

grant execute on function public.billing_price_paise() to service_role;
grant execute on function public.is_pro_sub(public.subscriptions) to service_role;
grant execute on function public.next_billing_date(integer, timestamptz) to service_role;
grant execute on function public.begin_checkout(uuid, text, text) to service_role;
grant execute on function public.attach_payment_link(text, text, text) to service_role;
grant execute on function public.fail_checkout(text, text) to service_role;
