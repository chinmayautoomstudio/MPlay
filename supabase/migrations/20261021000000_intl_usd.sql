-- International (USD) Pro next to India (INR). New checkouts can be INR or USD; PayU SI/autopay stays INR-only.
-- Existing 4-argument begin_checkout callers keep working (p_currency defaults to INR).

-- Columns -------------------------------------------------------------------------------------------------

do $$
declare
    r record;
begin
    for r in
        select c.conname, c.conrelid::regclass as tbl
        from pg_constraint c
        where c.contype = 'c'
          and (
              (c.conrelid = 'public.payments'::regclass and pg_get_constraintdef(c.oid) ilike '%currency%')
              or (c.conrelid = 'public.profiles'::regclass and pg_get_constraintdef(c.oid) ilike '%phone%')
          )
    loop
        execute format('alter table %s drop constraint %I', r.tbl, r.conname);
    end loop;
end $$;

alter table public.payments
    add constraint payments_currency_check check (currency in ('INR', 'USD'));

alter table public.profiles
    add constraint profiles_phone_check
        check (phone is null or phone ~ '^[6-9][0-9]{9}$' or phone ~ '^\+[1-9][0-9]{7,14}$');

alter table public.subscriptions
    add column if not exists currency text not null default 'INR';

do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conrelid = 'public.subscriptions'::regclass and conname = 'subscriptions_currency_check'
    ) then
        alter table public.subscriptions
            add constraint subscriptions_currency_check check (currency in ('INR', 'USD'));
    end if;
end $$;

-- Prices --------------------------------------------------------------------------------------------------

-- $1.99 a month or $19.99 a year (cents), or the INR amounts. One-arg billing_price_paise stays INR.
create function public.billing_price_paise(p_interval text, p_currency text)
returns integer
language sql
immutable
set search_path = ''
as $$
    select case
        when p_currency = 'USD' and p_interval = 'year' then 1999
        when p_currency = 'USD' then 199
        when p_interval = 'year' then 99900
        else 9900
    end
$$;

-- Entitlements --------------------------------------------------------------------------------------------

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
        'hasIndianPhone', v_profile.phone ~ '^[6-9][0-9]{9}$',
        'trial', case when v_trial.user_id is null then null else jsonb_build_object(
            'startedAt', v_trial.started_at,
            'endsAt', v_trial.ends_at
        ) end,
        'subscription', case when v_sub.id is null then null else jsonb_build_object(
            'status', v_sub.status,
            'provider', v_sub.provider,
            'interval', v_sub.billing_interval,
            'currency', v_sub.currency,
            'startedAt', v_sub.started_at,
            'expiresAt', v_sub.expires_at,
            'nextBillingAt', v_sub.next_billing_at,
            'cancelAtPeriodEnd', v_sub.cancel_at_period_end,
            'autopayStatus', v_sub.autopay_status,
            'graceEnd', v_sub.grace_end
        ) end,
        'billing', case when v_billing.id is null then null else jsonb_build_object(
            'status', v_billing.status,
            'interval', v_billing.billing_interval,
            'currency', v_billing.currency,
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
            'currency', v_last.currency,
            'at', v_last.completed_at
        ) end,
        'serverTime', now()
    );
end;
$$;

-- Checkout ------------------------------------------------------------------------------------------------

drop function public.begin_checkout(uuid, text, text, text);

-- p_currency is 'INR' or 'USD' (default INR). USD is always a one-off link (PayU SI is INR-only).
-- A replace of an existing PayU subscription keeps that row's currency.
create function public.begin_checkout(
    p_user uuid, p_phone text, p_mode text, p_interval text, p_currency text default 'INR'
)
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
    v_price integer;
    v_currency text;
    v_mode text;
    v_expires timestamptz := now() + interval '2 hours';
begin
    if p_mode is null or p_mode not in ('autopay', 'manual') then
        raise exception 'invalid mode %', p_mode using errcode = '22023';
    end if;
    if p_interval is null or p_interval not in ('month', 'year') then
        raise exception 'invalid interval %', p_interval using errcode = '22023';
    end if;
    if p_currency is null or p_currency not in ('INR', 'USD') then
        raise exception 'invalid currency %', p_currency using errcode = '22023';
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
        if p_currency = 'USD' then
            if p_phone !~ '^[6-9][0-9]{9}$' and p_phone !~ '^\+[1-9][0-9]{7,14}$' then
                return jsonb_build_object('error', 'invalid_phone');
            end if;
        elsif p_phone !~ '^[6-9][0-9]{9}$' then
            return jsonb_build_object('error', 'invalid_phone');
        end if;
        update public.profiles set phone = p_phone where id = p_user;
        v_profile.phone := p_phone;
    end if;

    select * into v_open from public.payments
    where user_id = p_user and status in ('created', 'pending')
    order by created_at desc
    limit 1;
    if found then
        if v_open.status = 'created' and v_open.link_url is not null
           and v_open.billing_interval = p_interval and v_open.currency = p_currency
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
        v_currency := p_currency;
        insert into public.subscriptions (user_id, plan, status, provider, billing_interval, currency)
        values (p_user, 'pro', 'pending', 'payu', p_interval, v_currency)
        returning * into v_sub;
    elsif v_sub.status = 'pending' then
        v_kind := 'first';
        v_currency := p_currency;
        update public.subscriptions
        set billing_interval = p_interval, currency = v_currency, updated_at = now()
        where id = v_sub.id;
        v_sub.billing_interval := p_interval;
        v_sub.currency := v_currency;
    else
        v_currency := v_sub.currency;
        if v_sub.autopay_status = 'on' then
            if v_sub.status = 'active' and not v_sub.cancel_at_period_end then
                if v_sub.billing_interval = p_interval then
                    return jsonb_build_object('error', 'already_subscribed');
                end if;
                if v_sub.expires_at > now() + interval '31 days' then
                    return jsonb_build_object('error', 'switch_not_yet',
                                              'availableFrom', v_sub.expires_at - interval '31 days');
                end if;
            end if;
            return jsonb_build_object('error', 'mandate_active', 'subscriptionId', v_sub.id);
        end if;
        v_mode := case when v_currency = 'USD' then 'manual' else p_mode end;
        if v_mode = 'manual' and v_sub.status = 'active' and v_sub.expires_at > now() + interval '7 days' then
            return jsonb_build_object('error', 'already_subscribed');
        end if;
        v_kind := 'replace';
    end if;

    v_mode := case when v_currency = 'USD' then 'manual' else p_mode end;
    v_price := public.billing_price_paise(p_interval, v_currency);

    if v_currency = 'INR' then
        if v_profile.phone is null or v_profile.phone !~ '^[6-9][0-9]{9}$' then
            return jsonb_build_object('error', 'phone_required');
        end if;
    elsif v_profile.phone is null then
        return jsonb_build_object('error', 'phone_required');
    end if;

    if v_kind = 'replace' and v_sub.status in ('active', 'cancelled') and v_sub.expires_at > now() then
        v_start := v_sub.expires_at;
        v_anchor := coalesce(v_sub.billing_anchor_day,
                             extract(day from v_start at time zone 'Asia/Kolkata')::integer);
    else
        v_start := now();
        v_anchor := extract(day from now() at time zone 'Asia/Kolkata')::integer;
    end if;

    v_txn := 'MP' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 20));
    insert into public.payments (txn_id, user_id, subscription_id, kind, status, si, billing_interval, amount_paise,
                                 currency, link_expires_at)
    values (v_txn, p_user, v_sub.id, v_kind, 'created', v_mode = 'autopay', p_interval, v_price, v_currency, v_expires);

    perform public.billing_event(p_user, 'payment_initiated', v_txn || ':initiated', v_txn, null, v_price,
                                 jsonb_build_object('kind', v_kind, 'mode', v_mode, 'interval', p_interval,
                                                    'currency', v_currency));

    return jsonb_build_object(
        'result', 'created',
        'txnId', v_txn,
        'kind', v_kind,
        'interval', p_interval,
        'currency', v_currency,
        'amountPaise', v_price,
        'linkExpiresAt', v_expires,
        'periodEnd', public.billing_period_end(p_interval, v_anchor, v_start),
        'customer', jsonb_build_object(
            'name', coalesce(nullif(btrim(v_profile.display_name), ''), split_part(v_profile.email, '@', 1)),
            'email', v_profile.email,
            'phone', v_profile.phone
        )
    );
end;
$$;

-- Verification: reject a PayU currency that does not match the payment row; copy currency onto the subscription.
create or replace function public.apply_payment_result(p_txn text, p_result jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_payment public.payments;
    v_sub public.subscriptions;
    v_status text := p_result ->> 'status';
    v_payu_ref text := nullif(p_result ->> 'payuRef', '');
    v_mandate text := nullif(p_result ->> 'mandateRef', '');
    v_method text := nullif(left(p_result ->> 'method', 40), '');
    v_reason text := nullif(left(p_result ->> 'reason', 200), '');
    v_currency text := nullif(upper(p_result ->> 'currency'), '');
    v_start timestamptz;
    v_end timestamptz;
    v_anchor integer;
    v_autopay text;
    v_continuing boolean;
begin
    if v_status is null or v_status not in ('success', 'failure', 'pending', 'not_found') then
        raise exception 'invalid status %', v_status using errcode = '22023';
    end if;

    select * into v_payment from public.payments where txn_id = p_txn for update;
    if not found then
        return jsonb_build_object('error', 'not_found');
    end if;
    if v_payment.status in ('success', 'refunded', 'partially_refunded', 'disputed') then
        return jsonb_build_object('result', 'unchanged', 'status', v_payment.status, 'kind', v_payment.kind);
    end if;

    select * into v_sub from public.subscriptions where id = v_payment.subscription_id for update;

    if v_status = 'success' then
        if coalesce(p_result ->> 'txnId', '') <> v_payment.txn_id
           or (p_result ->> 'amountPaise')::integer is distinct from v_payment.amount_paise
           or (v_currency is not null and v_currency is distinct from v_payment.currency)
           or v_payu_ref is null
           or exists (select 1 from public.payments where payu_ref = v_payu_ref and id <> v_payment.id) then
            update public.payments
            set status = 'failed', failure_reason = 'mismatch', completed_at = now(), updated_at = now(),
                last_checked_at = now()
            where id = v_payment.id;
            perform public.billing_event(v_payment.user_id, 'verification_mismatch', p_txn || ':mismatch', p_txn,
                                         v_payu_ref, v_payment.amount_paise, p_result - 'reason');
            return jsonb_build_object('error', 'mismatch');
        end if;

        if v_sub.id is null or (v_sub.status in ('failed', 'expired') and exists (
            select 1 from public.subscriptions
            where user_id = v_sub.user_id and provider = 'payu' and id <> v_sub.id
              and status in ('pending', 'active', 'past_due')
        )) then
            update public.payments
            set status = 'success', payu_ref = v_payu_ref, method = v_method, completed_at = now(),
                updated_at = now(), last_checked_at = now()
            where id = v_payment.id;
            perform public.billing_event(v_payment.user_id, 'payment_successful', p_txn || ':success', p_txn,
                                         v_payu_ref, v_payment.amount_paise, null);
            perform public.billing_event(v_payment.user_id, 'payment_needs_review', p_txn || ':review', p_txn,
                                         v_payu_ref, v_payment.amount_paise, null);
            return jsonb_build_object('result', 'applied', 'status', 'success', 'kind', v_payment.kind,
                                      'review', true);
        end if;

        v_continuing := v_payment.kind = 'renewal'
            or (v_sub.status in ('active', 'cancelled') and v_sub.expires_at > now());
        if v_continuing then
            v_start := coalesce(v_sub.expires_at, now());
            v_anchor := coalesce(v_sub.billing_anchor_day,
                                 extract(day from v_start at time zone 'Asia/Kolkata')::integer);
        else
            v_start := now();
            v_anchor := extract(day from now() at time zone 'Asia/Kolkata')::integer;
        end if;
        v_end := public.billing_period_end(v_payment.billing_interval, v_anchor, v_start);

        if v_payment.kind = 'renewal' then
            v_autopay := v_sub.autopay_status;
        elsif v_payment.si and v_mandate is not null then
            v_autopay := 'on';
        elsif v_payment.si then
            v_autopay := 'not_set';
        else
            v_autopay := 'off';
        end if;

        update public.payments
        set status = 'success', payu_ref = v_payu_ref, method = coalesce(v_method, method),
            mandate_ref = case when v_payment.kind = 'renewal' then v_sub.mandate_ref else v_mandate end,
            period_start = v_start, period_end = v_end, failure_reason = null,
            completed_at = now(), updated_at = now(), last_checked_at = now()
        where id = v_payment.id;

        update public.subscriptions
        set status = 'active',
            started_at = case when v_sub.status in ('pending', 'failed', 'expired') or v_sub.started_at is null
                              then now() else v_sub.started_at end,
            billing_interval = v_payment.billing_interval,
            currency = v_payment.currency,
            expires_at = v_end,
            next_billing_at = case when v_autopay = 'on' then v_end end,
            last_payment_at = now(),
            payment_status = 'paid',
            billing_anchor_day = v_anchor,
            autopay_status = v_autopay,
            cancel_at_period_end = v_autopay <> 'on',
            grace_end = null,
            renewal_attempts = 0,
            pre_debit_for = null,
            pre_debit_sent_at = null,
            mandate_ref = case when v_payment.kind = 'renewal' then v_sub.mandate_ref
                               when v_autopay = 'on' then v_mandate end,
            mandate_start = case when v_payment.kind = 'renewal' then v_sub.mandate_start
                                 when v_autopay = 'on' then v_end end,
            mandate_end = case when v_payment.kind = 'renewal' then v_sub.mandate_end
                               when v_autopay = 'on' then v_end + interval '5 years' end,
            mandate_cancel_requested_at = case when v_payment.kind = 'renewal'
                                               then v_sub.mandate_cancel_requested_at end,
            mandate_cancelled_at = case when v_payment.kind = 'renewal' then v_sub.mandate_cancelled_at end,
            updated_at = now()
        where id = v_sub.id;

        perform public.billing_event(v_payment.user_id, 'payment_successful', p_txn || ':success', p_txn,
                                     v_payu_ref, v_payment.amount_paise, jsonb_build_object('method', v_method));
        perform public.billing_event(v_payment.user_id,
            case when v_payment.kind = 'renewal' then 'subscription_renewed' else 'subscription_activated' end,
            p_txn || ':subscription', p_txn, v_payu_ref, v_payment.amount_paise,
            jsonb_build_object('expiresAt', v_end, 'autopay', v_autopay, 'interval', v_payment.billing_interval,
                               'currency', v_payment.currency));
        if v_payment.kind <> 'renewal' and v_autopay = 'on' then
            perform public.billing_event(v_payment.user_id, 'mandate_registered', p_txn || ':mandate', p_txn,
                                         v_mandate, null, null);
        end if;
        return jsonb_build_object('result', 'applied', 'status', 'success', 'kind', v_payment.kind,
                                  'subscriptionStatus', 'active');
    end if;

    if v_status = 'pending' then
        if v_payment.resolve_until is not null and v_payment.resolve_until < now() then
            v_status := 'failure';
            v_reason := 'timeout';
        else
            update public.payments
            set status = 'pending', method = coalesce(v_method, method), payu_ref = coalesce(v_payu_ref, payu_ref),
                resolve_until = coalesce(resolve_until, greatest(coalesce(link_expires_at, now()), now())
                    + case when coalesce(v_method, method) in ('ENACH', 'NACH', 'EMANDATE') then interval '3 days'
                           else interval '24 hours' end),
                last_checked_at = now(), updated_at = now()
            where id = v_payment.id;
            return jsonb_build_object('result', 'applied', 'status', 'pending', 'kind', v_payment.kind);
        end if;
    end if;

    if v_status = 'not_found' then
        if (v_payment.kind = 'renewal' and v_payment.created_at < now() - interval '1 hour')
           or (v_payment.kind <> 'renewal' and v_payment.link_expires_at < now() - interval '15 minutes') then
            v_status := 'failure';
            v_reason := case when v_payment.kind = 'renewal' then 'debit_not_registered' else 'link_expired' end;
        else
            update public.payments set last_checked_at = now() where id = v_payment.id;
            return jsonb_build_object('result', 'unchanged', 'status', v_payment.status, 'kind', v_payment.kind);
        end if;
    end if;

    if v_payment.status = 'failed' or v_payment.status = 'cancelled' then
        return jsonb_build_object('result', 'unchanged', 'status', v_payment.status, 'kind', v_payment.kind);
    end if;
    update public.payments
    set status = case when v_reason = 'link_expired' then 'cancelled' else 'failed' end,
        payu_ref = coalesce(v_payu_ref, payu_ref), method = coalesce(v_method, method),
        failure_reason = coalesce(v_reason, 'failed'), completed_at = now(), updated_at = now(), last_checked_at = now()
    where id = v_payment.id;
    perform public.billing_event(v_payment.user_id, 'payment_failed', p_txn || ':failed', p_txn, v_payu_ref,
                                 v_payment.amount_paise, jsonb_build_object('reason', coalesce(v_reason, 'failed')));

    if v_payment.kind = 'first' and v_sub.status = 'pending' then
        update public.subscriptions set status = 'failed', updated_at = now() where id = v_sub.id;
    elsif v_payment.kind = 'renewal' and v_sub.status in ('active', 'past_due') then
        update public.subscriptions
        set status = 'past_due',
            grace_end = coalesce(grace_end, greatest(coalesce(expires_at, now()), now()) + interval '3 days'),
            renewal_attempts = renewal_attempts + 1,
            payment_status = 'failed',
            updated_at = now()
        where id = v_sub.id
        returning * into v_sub;
        perform public.billing_event(v_payment.user_id, 'renewal_failed', p_txn || ':renewal_failed', p_txn,
                                     v_payu_ref, v_payment.amount_paise,
                                     jsonb_build_object('graceEnd', v_sub.grace_end, 'reason', v_reason));
    end if;
    return jsonb_build_object('result', 'applied', 'status', 'failed', 'kind', v_payment.kind,
                              'subscriptionStatus', v_sub.status);
end;
$$;

-- Renewals use the subscription's currency (USD rows have si = false and are never claimed here).
create or replace function public.claim_pre_debits(p_limit integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_rows jsonb;
begin
    with picked as (
        select s.id from public.subscriptions s
        where s.provider = 'payu' and s.status = 'active' and s.autopay_status = 'on' and not s.cancel_at_period_end
          and s.mandate_cancel_requested_at is null and s.mandate_ref is not null
          and s.currency = 'INR'
          and s.next_billing_at <= now() + interval '2 days'
          and s.pre_debit_for is distinct from s.next_billing_at
        limit least(greatest(p_limit, 1), 200)
        for update skip locked
    ), touched as (
        update public.subscriptions s
        set pre_debit_for = s.next_billing_at, pre_debit_sent_at = null
        from picked where s.id = picked.id
        returning s.id
    )
    select coalesce(jsonb_agg(jsonb_build_object(
        'subscriptionId', s.id,
        'mandateRef', s.mandate_ref,
        'debitDate', s.next_billing_at,
        'amountPaise', public.billing_price_paise(s.billing_interval, s.currency),
        'customer', public.billing_customer(s)
    )), '[]'::jsonb) into v_rows
    from touched t join public.subscriptions s on s.id = t.id;
    return v_rows;
end;
$$;

create or replace function public.claim_renewals(p_limit integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_sub public.subscriptions;
    v_txn text;
    v_price integer;
    v_rows jsonb := '[]'::jsonb;
begin
    for v_sub in
        select * from public.subscriptions s
        where s.provider = 'payu' and s.autopay_status = 'on' and not s.cancel_at_period_end
          and s.mandate_cancel_requested_at is null and s.mandate_ref is not null
          and s.currency = 'INR'
          and (
              (s.status = 'active' and s.next_billing_at <= now() and s.pre_debit_for = s.next_billing_at
               and s.pre_debit_sent_at <= now() - interval '24 hours')
              or (s.status = 'past_due' and s.grace_end > now() and s.renewal_attempts < 3
                  and not exists (select 1 from public.payments p
                                  where p.subscription_id = s.id and p.kind = 'renewal'
                                    and p.created_at > now() - interval '24 hours'))
          )
          and not exists (select 1 from public.payments p
                          where p.subscription_id = s.id and p.status in ('created', 'pending'))
        limit least(greatest(p_limit, 1), 100)
        for update skip locked
    loop
        if v_sub.mandate_end is not null and v_sub.mandate_end < v_sub.expires_at then
            perform public.billing_event(v_sub.user_id, 'mandate_ending', 'mandate_end:' || v_sub.id || ':'
                                         || v_sub.mandate_end, null, v_sub.mandate_ref, null, null);
        end if;

        v_price := public.billing_price_paise(v_sub.billing_interval, v_sub.currency);
        v_txn := 'RN' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 20));
        insert into public.payments (txn_id, user_id, subscription_id, kind, status, si, billing_interval,
                                     amount_paise, currency, period_start, attempt, mandate_ref)
        values (v_txn, v_sub.user_id, v_sub.id, 'renewal', 'created', true, v_sub.billing_interval, v_price,
                v_sub.currency, v_sub.expires_at, v_sub.renewal_attempts + 1, v_sub.mandate_ref)
        on conflict do nothing;
        if found then
            perform public.billing_event(v_sub.user_id, 'payment_initiated', v_txn || ':initiated', v_txn,
                                         v_sub.mandate_ref, v_price,
                                         jsonb_build_object('kind', 'renewal', 'attempt', v_sub.renewal_attempts + 1,
                                                            'interval', v_sub.billing_interval,
                                                            'currency', v_sub.currency));
            v_rows := v_rows || jsonb_build_object(
                'txnId', v_txn,
                'subscriptionId', v_sub.id,
                'mandateRef', v_sub.mandate_ref,
                'amountPaise', v_price,
                'customer', public.billing_customer(v_sub)
            );
        end if;
    end loop;
    return v_rows;
end;
$$;

create or replace function public.admin_user_detail(p_actor uuid, p_user uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_profile public.profiles;
    v_week date := public.usage_week_start(now());
begin
    perform public.assert_admin(p_actor);
    select * into v_profile from public.profiles where id = p_user;
    if not found then
        return jsonb_build_object('error', 'not_found');
    end if;

    return jsonb_build_object(
        'profile', jsonb_build_object(
            'id', v_profile.id,
            'email', v_profile.email,
            'name', v_profile.display_name,
            'avatarUrl', v_profile.avatar_url,
            'role', v_profile.role,
            'disabled', v_profile.disabled,
            'createdAt', v_profile.created_at
        ),
        'entitlements', public.compute_entitlements(p_user),
        'usage', public.usage_summary(p_user),
        'weeks', coalesce((
            select jsonb_agg(jsonb_build_object(
                'weekStart', w.week,
                'completed', (select count(*) from public.ai_usage a
                              where a.user_id = p_user and a.week_start = w.week and a.status = 'completed'),
                'released', (select count(*) from public.ai_usage a
                             where a.user_id = p_user and a.week_start = w.week and a.status = 'released'),
                'denied', (select count(*) from public.ai_usage a
                           where a.user_id = p_user and a.week_start = w.week and a.status = 'denied')
            ) order by w.week desc)
            from (select (v_week - 7 * g)::date as week from generate_series(0, 7) g) w
        ), '[]'::jsonb),
        'jobs', coalesce((
            select jsonb_agg(jsonb_build_object(
                'jobRef', a.job_ref,
                'songRef', a.song_ref,
                'status', a.status,
                'reservedAt', a.reserved_at,
                'completedAt', a.completed_at
            ) order by a.reserved_at desc)
            from (select * from public.ai_usage where user_id = p_user order by reserved_at desc limit 50) a
        ), '[]'::jsonb),
        'subscriptions', coalesce((
            select jsonb_agg(jsonb_build_object(
                'provider', s.provider,
                'status', s.status,
                'interval', s.billing_interval,
                'currency', s.currency,
                'startedAt', s.started_at,
                'expiresAt', s.expires_at,
                'nextBillingAt', s.next_billing_at,
                'lastPaymentAt', s.last_payment_at,
                'paymentStatus', s.payment_status,
                'cancelAtPeriodEnd', s.cancel_at_period_end,
                'autopayStatus', s.autopay_status,
                'graceEnd', s.grace_end,
                'mandateEnd', s.mandate_end,
                'cancelPending', s.mandate_cancel_requested_at is not null and s.autopay_status = 'on'
            ) order by s.created_at desc)
            from public.subscriptions s where s.user_id = p_user
        ), '[]'::jsonb),
        'payments', coalesce((
            select jsonb_agg(jsonb_build_object(
                'txnId', p.txn_id,
                'kind', p.kind,
                'interval', p.billing_interval,
                'status', p.status,
                'amountPaise', p.amount_paise,
                'currency', p.currency,
                'refundedPaise', p.refunded_paise,
                'payuRef', p.payu_ref,
                'method', p.method,
                'failureReason', p.failure_reason,
                'refundPending', p.refund_request_id is not null and p.status = 'success',
                'createdAt', p.created_at,
                'completedAt', p.completed_at
            ) order by p.created_at desc)
            from (select * from public.payments where user_id = p_user order by created_at desc limit 50) p
        ), '[]'::jsonb),
        'events', coalesce((
            select jsonb_agg(jsonb_build_object(
                'provider', e.provider,
                'type', e.event_type,
                'txnId', e.txn_id,
                'reference', e.provider_ref,
                'amountPaise', e.amount_paise,
                'currency', e.currency,
                'createdAt', e.created_at
            ) order by e.created_at desc, e.id desc)
            from (select * from public.payment_events where user_id = p_user order by created_at desc, id desc limit 50) e
        ), '[]'::jsonb)
    );
end;
$$;

revoke execute on function public.billing_price_paise(text, text) from public, anon, authenticated;
revoke execute on function public.begin_checkout(uuid, text, text, text, text) from public, anon, authenticated;

grant execute on function public.billing_price_paise(text, text) to service_role;
grant execute on function public.begin_checkout(uuid, text, text, text, text) to service_role;
