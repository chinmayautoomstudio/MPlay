-- Scheduled billing jobs, renewals, expiry, cancellation and mandate handling (payments PRD 6.3, 6.4, 6.11).
-- pg_cron calls the `billing-jobs` Edge Function through pg_net; the function URL and the job secret it checks are
-- read from Supabase Vault (`billing_functions_url`, `billing_jobs_secret`), never stored in code (JB1). Rows are
-- claimed with FOR UPDATE SKIP LOCKED and every step is idempotent, so a job can run twice or in parallel (JB3).
-- Everything is service role only.

create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;

-- Job runs (JB4) ------------------------------------------------------------------------------------------

create table public.job_runs (
    id bigint generated always as identity primary key,
    job text not null,
    started_at timestamptz not null,
    finished_at timestamptz not null default now(),
    processed integer not null default 0,
    failed integer not null default 0,
    skipped integer not null default 0,
    error text
);

create index job_runs_job_idx on public.job_runs (job, finished_at desc);
alter table public.job_runs enable row level security;
revoke all on public.job_runs from anon, authenticated;

create function public.record_job_run(
    p_job text, p_started timestamptz, p_processed integer, p_failed integer, p_skipped integer, p_error text
)
returns void
language sql
security definer
set search_path = ''
as $$
    insert into public.job_runs (job, started_at, processed, failed, skipped, error)
    values (p_job, p_started, coalesce(p_processed, 0), coalesce(p_failed, 0), coalesce(p_skipped, 0),
            left(p_error, 500));
    delete from public.job_runs where finished_at < now() - interval '30 days';
$$;

-- Claims ----------------------------------------------------------------------------------------------------

-- Open payments for the sweeper (CK9): expired links, attempts PayU reports as pending, and renewal debits, each
-- at most every 15 minutes. Attempts that never got a link (the server failed between the two steps) are closed
-- here. Returns [{txnId, si}].
create function public.claim_open_payments(p_limit integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_rows jsonb;
    v_stale record;
begin
    for v_stale in
        select txn_id from public.payments
        where status = 'created' and link_url is null and kind <> 'renewal' and created_at < now() - interval '10 minutes'
        for update skip locked
    loop
        perform public.fail_checkout(v_stale.txn_id, 'link_not_created');
    end loop;

    with picked as (
        select id from public.payments
        where status in ('created', 'pending')
          and (status = 'pending' or kind = 'renewal' or link_expires_at < now())
          and (last_checked_at is null or last_checked_at < now() - interval '15 minutes')
          and (link_url is not null or kind = 'renewal')
        order by last_checked_at nulls first
        limit least(greatest(p_limit, 1), 200)
        for update skip locked
    ), touched as (
        update public.payments p set last_checked_at = now()
        from picked where p.id = picked.id
        returning p.txn_id, p.si
    )
    select coalesce(jsonb_agg(jsonb_build_object('txnId', txn_id, 'si', si)), '[]'::jsonb) into v_rows from touched;
    return v_rows;
end;
$$;

-- Webhooks still to process: received but never finished (the function stopped), or failed and due again (SV7).
create function public.claim_webhooks(p_limit integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_rows jsonb;
begin
    with picked as (
        select id from public.webhook_log
        where status in ('received', 'failed') and not flagged and next_attempt_at <= now()
        order by id
        limit least(greatest(p_limit, 1), 100)
        for update skip locked
    ), touched as (
        update public.webhook_log w
        set attempts = w.attempts + 1, next_attempt_at = now() + interval '10 minutes'
        from picked where w.id = picked.id
        returning w.id, w.body
    )
    select coalesce(jsonb_agg(jsonb_build_object('id', id, 'body', body) order by id), '[]'::jsonb) into v_rows
    from touched;
    return v_rows;
end;
$$;

-- The customer details PayU needs for a debit or notice, and the payment method the mandate was set up with.
create function public.billing_customer(p_sub public.subscriptions)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
    select jsonb_build_object(
        'name', coalesce(nullif(btrim(p.display_name), ''), split_part(p.email, '@', 1)),
        'email', p.email,
        'phone', p.phone,
        'method', (select method from public.payments
                   where subscription_id = p_sub.id and mandate_ref = p_sub.mandate_ref and status = 'success'
                   order by completed_at limit 1)
    )
    from public.profiles p where p.id = p_sub.user_id
$$;

-- Subscriptions whose next debit is within 2 days and that haven't had a pre-debit notice for it (RN2). The claim
-- marks the notice as in flight; mark_pre_debit records the result. Returns [{subscriptionId, mandateRef,
-- debitDate, amountPaise, customer}].
create function public.claim_pre_debits(p_limit integer)
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
        'amountPaise', public.billing_price_paise(),
        'customer', public.billing_customer(s)
    )), '[]'::jsonb) into v_rows
    from touched t join public.subscriptions s on s.id = t.id;
    return v_rows;
end;
$$;

-- p_sent true: the notice went out. false: it failed and is tried again on the next run.
create function public.mark_pre_debit(p_sub uuid, p_sent boolean)
returns void
language sql
security definer
set search_path = ''
as $$
    update public.subscriptions
    set pre_debit_sent_at = case when p_sent then now() end,
        pre_debit_for = case when p_sent then pre_debit_for end,
        updated_at = now()
    where id = p_sub;
$$;

-- Renewal debits that are due (RN1, RN8). A first attempt needs the billing date reached and the pre-debit notice
-- sent at least 24 hours earlier; a past_due subscription retries once a day, up to 3 attempts, inside the grace
-- period (RN4). The payment row is unique per subscription, period and attempt, so a second run creates nothing.
-- Subscriptions whose mandate ends before the billing date are flagged (5.6). Returns [{txnId, subscriptionId,
-- mandateRef, amountPaise, customer}].
create function public.claim_renewals(p_limit integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_sub public.subscriptions;
    v_txn text;
    v_rows jsonb := '[]'::jsonb;
begin
    for v_sub in
        select * from public.subscriptions s
        where s.provider = 'payu' and s.autopay_status = 'on' and not s.cancel_at_period_end
          and s.mandate_cancel_requested_at is null and s.mandate_ref is not null
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

        v_txn := 'RN' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 20));
        insert into public.payments (txn_id, user_id, subscription_id, kind, status, si, amount_paise, period_start,
                                     attempt, mandate_ref)
        values (v_txn, v_sub.user_id, v_sub.id, 'renewal', 'created', true, public.billing_price_paise(),
                v_sub.expires_at, v_sub.renewal_attempts + 1, v_sub.mandate_ref)
        on conflict do nothing;
        if found then
            perform public.billing_event(v_sub.user_id, 'payment_initiated', v_txn || ':initiated', v_txn,
                                         v_sub.mandate_ref, public.billing_price_paise(),
                                         jsonb_build_object('kind', 'renewal', 'attempt', v_sub.renewal_attempts + 1));
            v_rows := v_rows || jsonb_build_object(
                'txnId', v_txn,
                'subscriptionId', v_sub.id,
                'mandateRef', v_sub.mandate_ref,
                'amountPaise', public.billing_price_paise(),
                'customer', public.billing_customer(v_sub)
            );
        end if;
    end loop;
    return v_rows;
end;
$$;

-- A renewal debit PayU refused to start: fail the attempt so the subscription goes past_due (RN4).
create function public.fail_renewal(p_txn text, p_reason text)
returns jsonb
language sql
security definer
set search_path = ''
as $$
    select public.apply_payment_result(p_txn, jsonb_build_object('status', 'failure', 'txnId', p_txn,
                                                                 'reason', left(p_reason, 200)));
$$;

-- Expiry (RN5, RN6) ---------------------------------------------------------------------------------------

-- Moves subscriptions on with time so access never depends on an app launch. Returns the counts.
create function public.expire_subscriptions()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_row record;
    v_expired integer := 0;
    v_past_due integer := 0;
    v_failed integer := 0;
begin
    -- Paid period over with no renewal coming: cancelled, autopay off/not_set/revoked, manual mode.
    for v_row in
        update public.subscriptions
        set status = 'expired', next_billing_at = null, updated_at = now()
        where provider = 'payu' and expires_at <= now()
          and (status = 'cancelled'
               or (status = 'active' and (coalesce(autopay_status, 'off') <> 'on' or cancel_at_period_end)))
        returning id, user_id
    loop
        v_expired := v_expired + 1;
        perform public.billing_event(v_row.user_id, 'subscription_expired', 'expired:' || v_row.id || ':'
                                     || now()::date, null, null, null, null);
    end loop;

    -- Grace period over without a successful retry.
    for v_row in
        update public.subscriptions
        set status = 'expired', next_billing_at = null, updated_at = now()
        where provider = 'payu' and status = 'past_due' and coalesce(grace_end, expires_at) <= now()
        returning id, user_id
    loop
        v_expired := v_expired + 1;
        perform public.billing_event(v_row.user_id, 'subscription_expired', 'expired:' || v_row.id || ':'
                                     || now()::date, null, null, null, jsonb_build_object('reason', 'grace_ended'));
    end loop;

    -- Autopay on but no successful debit a day after the period end (the debit never ran): past_due.
    for v_row in
        update public.subscriptions
        set status = 'past_due', grace_end = expires_at + interval '3 days', payment_status = 'failed',
            updated_at = now()
        where provider = 'payu' and status = 'active' and autopay_status = 'on' and not cancel_at_period_end
          and expires_at + interval '1 day' <= now()
          and not exists (select 1 from public.payments p
                          where p.subscription_id = subscriptions.id and p.status in ('created', 'pending'))
        returning id, user_id, grace_end
    loop
        v_past_due := v_past_due + 1;
        perform public.billing_event(v_row.user_id, 'renewal_failed', 'renewal_missed:' || v_row.id || ':'
                                     || now()::date, null, null, null,
                                     jsonb_build_object('graceEnd', v_row.grace_end, 'reason', 'not_debited'));
    end loop;

    -- Checkouts that never produced a payment.
    update public.subscriptions s
    set status = 'failed', updated_at = now()
    where s.provider = 'payu' and s.status = 'pending' and s.created_at < now() - interval '1 day'
      and not exists (select 1 from public.payments p where p.subscription_id = s.id and p.status in ('created', 'pending'));
    get diagnostics v_failed = row_count;

    return jsonb_build_object('expired', v_expired, 'pastDue', v_past_due, 'failed', v_failed);
end;
$$;

-- Notices (5.3) ---------------------------------------------------------------------------------------------

-- Emails due now: renewal failed (first failure), grace ending (a day before), expired (after a failed renewal or
-- cancellation), autopay mandate ending (30 days before). Each is returned once: the payment event with the
-- notice key is the record that it was sent. Returns [{type, userId, email, name, date}].
create function public.claim_notices(p_limit integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_row record;
    v_rows jsonb := '[]'::jsonb;
    v_key text;
    v_count integer := 0;
begin
    for v_row in
        select 'renewal_failed' as type, s.id, s.user_id, s.grace_end as at, s.expires_at as period
        from public.subscriptions s where s.provider = 'payu' and s.status = 'past_due'
        union all
        select 'grace_ending', s.id, s.user_id, s.grace_end, s.expires_at
        from public.subscriptions s
        where s.provider = 'payu' and s.status = 'past_due' and s.grace_end <= now() + interval '1 day'
        union all
        select 'expired', s.id, s.user_id, s.expires_at, s.expires_at
        from public.subscriptions s
        where s.provider = 'payu' and s.status = 'expired' and s.updated_at > now() - interval '2 days'
        union all
        select 'mandate_ending', s.id, s.user_id, s.mandate_end, s.mandate_end
        from public.subscriptions s
        where s.provider = 'payu' and s.status = 'active' and s.autopay_status = 'on'
          and s.mandate_end <= now() + interval '30 days'
    loop
        exit when v_count >= least(greatest(p_limit, 1), 200);
        v_key := 'notice:' || v_row.type || ':' || v_row.id || ':' || coalesce(v_row.period::text, '');
        insert into public.payment_events (user_id, provider, provider_event_id, event_type, payload)
        values (v_row.user_id, 'payu', v_key, 'notice_' || v_row.type, jsonb_build_object('date', v_row.at))
        on conflict (provider, provider_event_id) do nothing;
        if found then
            v_count := v_count + 1;
            v_rows := v_rows || (
                select jsonb_build_object('type', v_row.type, 'userId', v_row.user_id, 'email', p.email,
                                          'name', p.display_name, 'date', v_row.at)
                from public.profiles p where p.id = v_row.user_id
            );
        end if;
    end loop;
    return v_rows;
end;
$$;

-- Cancellation and mandates (CN1-CN6, 5.5) ------------------------------------------------------------------

-- Marks that the mandate on p_sub is being cancelled with PayU. A user cancellation (p_user_cancel) also stops
-- renewals at once (CN4: nothing is charged after a confirmed cancellation request). Returns {mandateRef, method}
-- for the PayU call, or {"result": "no_mandate"} when there is no live mandate.
create function public.request_mandate_cancel(p_sub uuid, p_user_cancel boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_sub public.subscriptions;
begin
    select * into v_sub from public.subscriptions where id = p_sub and provider = 'payu' for update;
    if not found then
        return jsonb_build_object('error', 'not_found');
    end if;
    update public.subscriptions
    set mandate_cancel_requested_at = case when autopay_status = 'on'
                                           then coalesce(mandate_cancel_requested_at, now()) end,
        cancel_at_period_end = cancel_at_period_end or p_user_cancel,
        next_billing_at = case when p_user_cancel then null else next_billing_at end,
        updated_at = now()
    where id = p_sub
    returning * into v_sub;
    if v_sub.autopay_status is distinct from 'on' or v_sub.mandate_ref is null then
        return jsonb_build_object('result', 'no_mandate');
    end if;
    return jsonb_build_object('mandateRef', v_sub.mandate_ref,
                              'method', public.billing_customer(v_sub) ->> 'method');
end;
$$;

-- PayU confirmed the mandate is no longer live. p_revoked: the customer revoked it at the bank or UPI app (RN7),
-- otherwise we cancelled it. A subscription set to cancel becomes cancelled; past_due keeps Pro to the grace end.
create function public.confirm_mandate_cancelled(p_sub uuid, p_revoked boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_sub public.subscriptions;
begin
    select * into v_sub from public.subscriptions where id = p_sub for update;
    if not found then
        return jsonb_build_object('error', 'not_found');
    end if;
    if v_sub.autopay_status is distinct from 'on' then
        return jsonb_build_object('result', 'unchanged', 'status', v_sub.status);
    end if;
    update public.subscriptions
    set autopay_status = case when p_revoked then 'revoked' else 'off' end,
        mandate_cancelled_at = now(),
        mandate_cancel_requested_at = null,
        next_billing_at = null,
        cancel_at_period_end = true,
        status = case when v_sub.cancel_at_period_end and v_sub.status in ('active', 'past_due') then 'cancelled'
                      else v_sub.status end,
        expires_at = case when v_sub.cancel_at_period_end and v_sub.status = 'past_due'
                          then coalesce(v_sub.grace_end, v_sub.expires_at) else v_sub.expires_at end,
        updated_at = now()
    where id = p_sub
    returning * into v_sub;
    perform public.billing_event(v_sub.user_id, case when p_revoked then 'mandate_revoked' else 'mandate_cancelled' end,
                                 'mandate_off:' || v_sub.id || ':' || coalesce(v_sub.mandate_ref, ''), null,
                                 v_sub.mandate_ref, null, null);
    if v_sub.status = 'cancelled' then
        perform public.billing_event(v_sub.user_id, 'subscription_cancelled', 'cancelled:' || v_sub.id || ':'
                                     || v_sub.expires_at, null, null, null,
                                     jsonb_build_object('endsAt', v_sub.expires_at));
    end if;
    return jsonb_build_object('result', 'cancelled', 'status', v_sub.status, 'expiresAt', v_sub.expires_at);
end;
$$;

-- The user's cancel request (CN1, CN2). Without a live mandate the subscription is cancelled at once; with one,
-- the caller cancels it with PayU and then calls confirm_mandate_cancelled. Returns {subscriptionId, mandateRef,
-- method}, {"result": "cancelled", expiresAt} or {"error": "not_subscribed"}.
create function public.begin_cancel(p_user uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_sub public.subscriptions;
    v_request jsonb;
begin
    perform pg_advisory_xact_lock(hashtextextended('billing:' || p_user::text, 0));
    select * into v_sub from public.subscriptions
    where user_id = p_user and provider = 'payu' and status in ('active', 'past_due')
    for update;
    if not found then
        return jsonb_build_object('error', 'not_subscribed');
    end if;

    v_request := public.request_mandate_cancel(v_sub.id, true);
    if v_request ->> 'result' = 'no_mandate' then
        update public.subscriptions
        set status = 'cancelled', cancel_at_period_end = true, next_billing_at = null,
            expires_at = case when status = 'past_due' then coalesce(grace_end, expires_at) else expires_at end,
            updated_at = now()
        where id = v_sub.id
        returning * into v_sub;
        perform public.billing_event(p_user, 'subscription_cancelled', 'cancelled:' || v_sub.id || ':'
                                     || v_sub.expires_at, null, null, null,
                                     jsonb_build_object('endsAt', v_sub.expires_at));
        return jsonb_build_object('result', 'cancelled', 'expiresAt', v_sub.expires_at);
    end if;
    return v_request || jsonb_build_object('subscriptionId', v_sub.id);
end;
$$;

-- Mandates whose cancellation PayU hasn't confirmed yet, for the retry job (CN4, 5.5). Each is tried at most
-- every 15 minutes. Returns [{subscriptionId, mandateRef, method}].
create function public.claim_mandate_cancels(p_limit integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_rows jsonb;
begin
    with picked as (
        select id from public.subscriptions
        where provider = 'payu' and autopay_status = 'on' and mandate_cancel_requested_at is not null
          and updated_at < now() - interval '15 minutes'
        limit least(greatest(p_limit, 1), 100)
        for update skip locked
    ), touched as (
        update public.subscriptions s set updated_at = now()
        from picked where s.id = picked.id
        returning s.id
    )
    select coalesce(jsonb_agg(jsonb_build_object(
        'subscriptionId', s.id,
        'mandateRef', s.mandate_ref,
        'method', public.billing_customer(s) ->> 'method'
    )), '[]'::jsonb) into v_rows
    from touched t join public.subscriptions s on s.id = t.id;
    return v_rows;
end;
$$;

-- Live mandates of a user, cancelled before the account is deleted (CN6). Returns [subscriptionId].
create function public.live_mandates(p_user uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
    select coalesce(jsonb_agg(id), '[]'::jsonb) from public.subscriptions
    where user_id = p_user and provider = 'payu' and autopay_status = 'on'
$$;

-- The subscription a mandate belongs to, for mandate webhooks (RN7).
create function public.subscription_by_mandate(p_mandate text)
returns uuid
language sql
stable
security definer
set search_path = ''
as $$
    select id from public.subscriptions where provider = 'payu' and mandate_ref = p_mandate
    order by created_at desc limit 1
$$;

-- Account deletion keeps payment records detached from the person (CN6): the foreign keys clear user_id and
-- subscription_id; the link URL is cleared too.
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

    update public.payments set link_url = null, status = case when status = 'created' then 'cancelled' else status end
    where user_id = p_user;
    perform public.admin_log(p_user, 'account_deleted', p_user,
                             jsonb_build_object('role', v_profile.role, 'plan', public.plan_of(p_user)));
    delete from auth.users where id = p_user;
    return jsonb_build_object('result', 'deleted');
end;
$$;

-- Scheduling (JB1, JB2) -------------------------------------------------------------------------------------

-- Calls the billing-jobs Edge Function for one action. Does nothing until both Vault secrets exist.
create function public.run_billing_job(p_action text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_url text;
    v_secret text;
begin
    select decrypted_secret into v_url from vault.decrypted_secrets where name = 'billing_functions_url';
    select decrypted_secret into v_secret from vault.decrypted_secrets where name = 'billing_jobs_secret';
    if v_url is null or v_secret is null then
        raise warning 'billing job % skipped: Vault secrets billing_functions_url and billing_jobs_secret are not set',
            p_action;
        return;
    end if;
    perform net.http_post(
        url := rtrim(v_url, '/') || '/billing-jobs',
        headers := jsonb_build_object('Content-Type', 'application/json', 'x-billing-job-secret', v_secret),
        body := jsonb_build_object('action', p_action),
        timeout_milliseconds := 120000
    );
end;
$$;

select cron.schedule('billing-webhooks', '*/5 * * * *', $$ select public.run_billing_job('webhooks') $$);
select cron.schedule('billing-sweep', '*/15 * * * *', $$ select public.run_billing_job('sweep') $$);
select cron.schedule('billing-renewals', '7 * * * *', $$ select public.run_billing_job('renewals') $$);
select cron.schedule('billing-expiry', '37 * * * *', $$ select public.run_billing_job('expiry') $$);
select cron.schedule('billing-notices', '30 3 * * *', $$ select public.run_billing_job('notices') $$);

-- Access --------------------------------------------------------------------------------------------------

revoke execute on function public.record_job_run(text, timestamptz, integer, integer, integer, text)
    from public, anon, authenticated;
revoke execute on function public.claim_open_payments(integer) from public, anon, authenticated;
revoke execute on function public.claim_webhooks(integer) from public, anon, authenticated;
revoke execute on function public.billing_customer(public.subscriptions) from public, anon, authenticated;
revoke execute on function public.claim_pre_debits(integer) from public, anon, authenticated;
revoke execute on function public.mark_pre_debit(uuid, boolean) from public, anon, authenticated;
revoke execute on function public.claim_renewals(integer) from public, anon, authenticated;
revoke execute on function public.fail_renewal(text, text) from public, anon, authenticated;
revoke execute on function public.expire_subscriptions() from public, anon, authenticated;
revoke execute on function public.claim_notices(integer) from public, anon, authenticated;
revoke execute on function public.request_mandate_cancel(uuid, boolean) from public, anon, authenticated;
revoke execute on function public.confirm_mandate_cancelled(uuid, boolean) from public, anon, authenticated;
revoke execute on function public.begin_cancel(uuid) from public, anon, authenticated;
revoke execute on function public.claim_mandate_cancels(integer) from public, anon, authenticated;
revoke execute on function public.live_mandates(uuid) from public, anon, authenticated;
revoke execute on function public.subscription_by_mandate(text) from public, anon, authenticated;
revoke execute on function public.run_billing_job(text) from public, anon, authenticated, service_role;

grant execute on function public.record_job_run(text, timestamptz, integer, integer, integer, text) to service_role;
grant execute on function public.claim_open_payments(integer) to service_role;
grant execute on function public.claim_webhooks(integer) to service_role;
grant execute on function public.claim_pre_debits(integer) to service_role;
grant execute on function public.mark_pre_debit(uuid, boolean) to service_role;
grant execute on function public.claim_renewals(integer) to service_role;
grant execute on function public.fail_renewal(text, text) to service_role;
grant execute on function public.expire_subscriptions() to service_role;
grant execute on function public.claim_notices(integer) to service_role;
grant execute on function public.request_mandate_cancel(uuid, boolean) to service_role;
grant execute on function public.confirm_mandate_cancelled(uuid, boolean) to service_role;
grant execute on function public.begin_cancel(uuid) to service_role;
grant execute on function public.claim_mandate_cancels(integer) to service_role;
grant execute on function public.live_mandates(uuid) to service_role;
grant execute on function public.subscription_by_mandate(text) to service_role;
