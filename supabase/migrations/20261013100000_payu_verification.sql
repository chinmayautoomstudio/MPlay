-- PayU verification and activation (payments PRD 6.2). The Edge Functions ask PayU for a payment's status and pass
-- the answer here; nothing changes state from a redirect or a webhook body alone (SV1, SV2). Each payment is
-- applied once: the row is locked, and a payment that already succeeded is left alone, so the webhook racing the
-- app's status check ends in one activation (SV6, SV8). Service role only.

-- Applies PayU's verified answer for one transaction. p_result is
-- {status: success | failure | pending | not_found, txnId, payuRef, amountPaise, method, mandateRef, reason}.
-- Returns {"result": "applied" | "unchanged", status, kind, subscriptionStatus}, {"error": "not_found"} or
-- {"error": "mismatch"} (amount, invoice or PayU reference doesn't match; logged, payment failed, SV3).
create function public.apply_payment_result(p_txn text, p_result jsonb)
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
    -- Settled payments never move again from a status check; refunds and disputes have their own functions.
    if v_payment.status in ('success', 'refunded', 'partially_refunded', 'disputed') then
        return jsonb_build_object('result', 'unchanged', 'status', v_payment.status, 'kind', v_payment.kind);
    end if;

    select * into v_sub from public.subscriptions where id = v_payment.subscription_id for update;

    if v_status = 'success' then
        if coalesce(p_result ->> 'txnId', '') <> v_payment.txn_id
           or (p_result ->> 'amountPaise')::integer is distinct from v_payment.amount_paise
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
            -- The subscription is gone (account deleted while paying), or a late payment arrived after the user
            -- started another subscription: keep the money record and flag it for Admins to refund.
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

        -- A renewal continues from the old period end; a payment while paid time remains extends it; anything
        -- else (first payment, Fix payment after the period ended) starts the month now (5.3, 5.5).
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
        v_end := public.next_billing_date(v_anchor, v_start);

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
            jsonb_build_object('expiresAt', v_end, 'autopay', v_autopay));
        if v_payment.kind <> 'renewal' and v_autopay = 'on' then
            perform public.billing_event(v_payment.user_id, 'mandate_registered', p_txn || ':mandate', p_txn,
                                         v_mandate, null, null);
        end if;
        return jsonb_build_object('result', 'applied', 'status', 'success', 'kind', v_payment.kind,
                                  'subscriptionStatus', 'active');
    end if;

    if v_status = 'pending' then
        -- Still in progress at PayU: keep checking until resolve_until (24 hours after the link expires, 3 days
        -- for eNACH), then fail it (CK9).
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
        -- PayU has no attempt: the link is unused. Close it once it has expired (renewal debits after an hour).
        if (v_payment.kind = 'renewal' and v_payment.created_at < now() - interval '1 hour')
           or (v_payment.kind <> 'renewal' and v_payment.link_expires_at < now() - interval '15 minutes') then
            v_status := 'failure';
            v_reason := case when v_payment.kind = 'renewal' then 'debit_not_registered' else 'link_expired' end;
        else
            update public.payments set last_checked_at = now() where id = v_payment.id;
            return jsonb_build_object('result', 'unchanged', 'status', v_payment.status, 'kind', v_payment.kind);
        end if;
    end if;

    -- Failure.
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

-- The caller's open payments to check with PayU (CK6, CK8): p_txn when it is theirs, otherwise all their open
-- ones. Each is checked at most every p_min_seconds, so a busy app can't hammer PayU. Returns [{txnId, si}].
create function public.payments_to_check(p_user uuid, p_txn text, p_min_seconds integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_rows jsonb;
begin
    with picked as (
        select id from public.payments
        where user_id = p_user and status in ('created', 'pending')
          and (p_txn is null or txn_id = p_txn)
          and (last_checked_at is null or last_checked_at < now() - make_interval(secs => greatest(p_min_seconds, 0)))
          and (link_url is not null or kind = 'renewal')
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

-- What the app shows on the payment result screen (CK7) for one of the caller's payments.
create function public.payment_summary(p_user uuid, p_txn text)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
    select coalesce((
        select jsonb_build_object(
            'txnId', p.txn_id,
            'status', p.status,
            'kind', p.kind,
            'failureReason', p.failure_reason,
            'resolveUntil', p.resolve_until,
            'amountPaise', p.amount_paise,
            'completedAt', p.completed_at,
            'plan', public.plan_of(p_user)
        )
        from public.payments p
        where p.user_id = p_user and (p_txn is null or p.txn_id = p_txn)
        order by p.created_at desc
        limit 1
    ), jsonb_build_object('error', 'not_found'));
$$;

-- Saves an incoming webhook before anything else (SV7). duplicate is true when an event with the same key was
-- already processed, so the caller records it and does nothing more.
create function public.log_webhook(p_key text, p_body jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_id bigint;
    v_duplicate boolean;
begin
    v_duplicate := p_key is not null and exists (
        select 1 from public.webhook_log where event_key = p_key and status = 'processed'
    );
    insert into public.webhook_log (event_key, body, status)
    values (left(p_key, 200), p_body, case when v_duplicate then 'ignored' else 'received' end)
    returning id into v_id;
    return jsonb_build_object('id', v_id, 'duplicate', v_duplicate);
end;
$$;

-- Records how processing a webhook ended. A failure is retried with backoff (5, 10, 20, 40 minutes ...) and
-- flagged for Admins after 5 attempts.
create function public.finish_webhook(p_id bigint, p_status text, p_error text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if p_status not in ('processed', 'failed', 'ignored') then
        raise exception 'invalid status %', p_status using errcode = '22023';
    end if;
    update public.webhook_log
    set status = p_status,
        last_error = left(p_error, 500),
        processed_at = case when p_status <> 'failed' then now() end,
        next_attempt_at = case when p_status = 'failed'
                               then now() + interval '5 minutes' * power(2, least(attempts, 8)) else next_attempt_at end,
        flagged = p_status = 'failed' and attempts >= 5
    where id = p_id;
end;
$$;

revoke execute on function public.apply_payment_result(text, jsonb) from public, anon, authenticated;
revoke execute on function public.payments_to_check(uuid, text, integer) from public, anon, authenticated;
revoke execute on function public.payment_summary(uuid, text) from public, anon, authenticated;
revoke execute on function public.log_webhook(text, jsonb) from public, anon, authenticated;
revoke execute on function public.finish_webhook(bigint, text, text) from public, anon, authenticated;

grant execute on function public.apply_payment_result(text, jsonb) to service_role;
grant execute on function public.payments_to_check(uuid, text, integer) to service_role;
grant execute on function public.payment_summary(uuid, text) to service_role;
grant execute on function public.log_webhook(text, jsonb) to service_role;
grant execute on function public.finish_webhook(bigint, text, text) to service_role;
