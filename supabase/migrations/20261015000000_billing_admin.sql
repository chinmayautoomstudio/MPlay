-- Admin billing views and actions, refunds and disputes (payments PRD 6.7, 6.10). Admin functions take p_actor
-- from the verified token, start with assert_admin and write the audit log. Refund and dispute results come from
-- PayU (status check or webhook) and are applied idempotently (RF4). Service role only.

-- Payments matching p_query (email, transaction ID or PayU reference) and p_filter (AD2).
create function public.admin_list_payments(
    p_actor uuid, p_query text, p_filter text, p_limit integer, p_offset integer
)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_pattern text := '%' || replace(replace(replace(coalesce(btrim(p_query), ''), '\', '\\'), '%', '\%'), '_', '\_') || '%';
    v_result jsonb;
begin
    perform public.assert_admin(p_actor);
    if p_filter not in ('all', 'success', 'failed', 'pending', 'refunded', 'disputed', 'past_due', 'cancelled') then
        raise exception 'invalid filter %', p_filter using errcode = '22023';
    end if;

    with matched as (
        select p.*, u.email, s.status as sub_status
        from public.payments p
        left join public.profiles u on u.id = p.user_id
        left join public.subscriptions s on s.id = p.subscription_id
        where coalesce(btrim(p_query), '') = ''
           or u.email ilike v_pattern or p.txn_id ilike v_pattern or p.payu_ref ilike v_pattern
    ), filtered as (
        select * from matched
        where case p_filter
            when 'all' then true
            when 'success' then status = 'success'
            when 'failed' then status in ('failed', 'cancelled')
            when 'pending' then status in ('created', 'pending')
            when 'refunded' then status in ('refunded', 'partially_refunded')
            when 'disputed' then status = 'disputed'
            when 'past_due' then sub_status = 'past_due'
            when 'cancelled' then sub_status = 'cancelled'
        end
    ), page as (
        select * from filtered
        order by created_at desc, id
        limit least(greatest(p_limit, 1), 100) offset greatest(p_offset, 0)
    )
    select jsonb_build_object(
        'total', (select count(*) from filtered),
        'payments', coalesce((
            select jsonb_agg(jsonb_build_object(
                'txnId', x.txn_id,
                'userId', x.user_id,
                'email', x.email,
                'kind', x.kind,
                'status', x.status,
                'amountPaise', x.amount_paise,
                'refundedPaise', x.refunded_paise,
                'payuRef', x.payu_ref,
                'method', x.method,
                'failureReason', x.failure_reason,
                'subscriptionStatus', x.sub_status,
                'refundPending', x.refund_request_id is not null and x.status = 'success',
                'createdAt', x.created_at,
                'completedAt', x.completed_at
            ) order by x.created_at desc, x.id)
            from page x
        ), '[]'::jsonb)
    ) into v_result;
    return v_result;
end;
$$;

-- Health of the billing jobs and queues (JB4, observability): last run per job and whether it is overdue (no run
-- for twice its interval), flagged webhooks, mandate cancellations PayU hasn't confirmed, payments to review.
create function public.admin_billing_health(p_actor uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    perform public.assert_admin(p_actor);
    return jsonb_build_object(
        'jobs', coalesce((
            select jsonb_agg(jsonb_build_object(
                'job', j.job,
                'lastRunAt', r.finished_at,
                'processed', r.processed,
                'failed', r.failed,
                'skipped', r.skipped,
                'error', r.error,
                'overdue', r.finished_at is null or r.finished_at < now() - 2 * j.every
            ) order by j.job)
            from (values ('webhooks', interval '5 minutes'), ('sweep', interval '15 minutes'),
                         ('renewals', interval '1 hour'), ('expiry', interval '1 hour'),
                         ('notices', interval '1 day')) as j (job, every)
            left join lateral (
                select * from public.job_runs where job = j.job order by finished_at desc limit 1
            ) r on true
        ), '[]'::jsonb),
        'flaggedWebhooks', (select count(*) from public.webhook_log where flagged),
        'failedWebhooks', (select count(*) from public.webhook_log where status = 'failed' and not flagged),
        'pendingMandateCancels', (
            select count(*) from public.subscriptions
            where provider = 'payu' and autopay_status = 'on' and mandate_cancel_requested_at is not null
        ),
        'needsReview', (
            select count(*) from public.payment_events
            where event_type in ('verification_mismatch', 'payment_needs_review', 'dispute_opened', 'mandate_ending')
              and created_at > now() - interval '30 days'
        )
    );
end;
$$;

-- Checks and logs an Admin billing action before the Edge Function calls PayU (AD3, AD4, RF2).
-- p_action: reverify (p_txn), refund (p_txn) or cancel_subscription (p_user). Returns what the call needs, or
-- {"error": "not_found" | "not_refundable" | "not_subscribed"}.
create function public.admin_billing_action(p_actor uuid, p_action text, p_user uuid, p_txn text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_payment public.payments;
    v_sub public.subscriptions;
begin
    perform public.assert_admin(p_actor);
    if p_action not in ('reverify', 'refund', 'cancel_subscription') then
        raise exception 'invalid action %', p_action using errcode = '22023';
    end if;

    if p_action = 'cancel_subscription' then
        select * into v_sub from public.subscriptions
        where user_id = p_user and provider = 'payu' and status in ('active', 'past_due');
        if not found then
            return jsonb_build_object('error', 'not_subscribed');
        end if;
        perform public.admin_log(p_actor, 'cancel_subscription', p_user, jsonb_build_object('subscription', v_sub.id));
        return jsonb_build_object('userId', p_user);
    end if;

    select * into v_payment from public.payments where txn_id = p_txn;
    if not found then
        return jsonb_build_object('error', 'not_found');
    end if;
    if p_action = 'refund' and (v_payment.status not in ('success', 'partially_refunded') or v_payment.payu_ref is null
                                or v_payment.refund_request_id is not null) then
        return jsonb_build_object('error', 'not_refundable');
    end if;
    perform public.admin_log(p_actor, case when p_action = 'refund' then 'refund_requested' else 'reverify_payment' end,
                             v_payment.user_id, jsonb_build_object('txnId', p_txn));
    return jsonb_build_object(
        'txnId', v_payment.txn_id,
        'si', v_payment.si,
        'status', v_payment.status,
        'payuRef', v_payment.payu_ref,
        'refundablePaise', v_payment.amount_paise - v_payment.refunded_paise,
        'refundRequestId', v_payment.refund_request_id
    );
end;
$$;

-- Refunds (RF2-RF4, RF7) ------------------------------------------------------------------------------------

create function public.record_refund_request(p_txn text, p_request_id text)
returns void
language sql
security definer
set search_path = ''
as $$
    update public.payments
    set refund_request_id = p_request_id, refund_requested_at = now(), updated_at = now()
    where txn_id = p_txn and refund_request_id is null;
$$;

-- Payments with a refund PayU hasn't settled yet, for the sweeper. Returns [{txnId, refundRequestId}].
create function public.claim_pending_refunds(p_limit integer)
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
        where refund_request_id is not null and status in ('success', 'partially_refunded', 'disputed')
          and (last_checked_at is null or last_checked_at < now() - interval '15 minutes')
        limit least(greatest(p_limit, 1), 100)
        for update skip locked
    ), touched as (
        update public.payments p set last_checked_at = now()
        from picked where p.id = picked.id
        returning p.txn_id, p.refund_request_id
    )
    select coalesce(jsonb_agg(jsonb_build_object('txnId', txn_id, 'refundRequestId', refund_request_id)), '[]'::jsonb)
    into v_rows from touched;
    return v_rows;
end;
$$;

-- Applies PayU's refund result. A full refund of the payment for the current period ends Pro now (RF3); the
-- caller cancels a live mandate with PayU first. A partial refund changes nothing else (RF7). Returns
-- {"result": "applied" | "unchanged", status, expired}.
create function public.apply_refund_result(p_txn text, p_state text, p_amount_paise integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_payment public.payments;
    v_refunded integer;
    v_status text;
    v_expired boolean := false;
begin
    if p_state not in ('success', 'failure') then
        raise exception 'invalid state %', p_state using errcode = '22023';
    end if;
    select * into v_payment from public.payments where txn_id = p_txn for update;
    if not found then
        return jsonb_build_object('error', 'not_found');
    end if;

    if p_state = 'failure' then
        update public.payments set refund_request_id = null, updated_at = now() where id = v_payment.id;
        perform public.billing_event(v_payment.user_id, 'refund_failed', p_txn || ':refund_failed:'
                                     || coalesce(v_payment.refund_request_id, ''), p_txn, v_payment.payu_ref, null, null);
        return jsonb_build_object('result', 'applied', 'status', v_payment.status, 'expired', false);
    end if;

    v_refunded := least(v_payment.amount_paise, greatest(v_payment.refunded_paise,
                                                         coalesce(p_amount_paise, v_payment.amount_paise)));
    if v_refunded = v_payment.refunded_paise and v_payment.status in ('refunded', 'partially_refunded') then
        return jsonb_build_object('result', 'unchanged', 'status', v_payment.status, 'expired', false);
    end if;
    v_status := case when v_refunded >= v_payment.amount_paise then 'refunded' else 'partially_refunded' end;

    update public.payments
    set refunded_paise = v_refunded, status = v_status, refund_request_id = null, updated_at = now()
    where id = v_payment.id;
    perform public.billing_event(v_payment.user_id, 'refund_issued', p_txn || ':refund:' || v_refunded, p_txn,
                                 v_payment.payu_ref, v_refunded, null);

    if v_status = 'refunded' then
        update public.subscriptions
        set status = 'expired', expires_at = now(), next_billing_at = null, cancel_at_period_end = true,
            grace_end = null, updated_at = now()
        where id = v_payment.subscription_id and status in ('active', 'past_due', 'cancelled')
          and v_payment.period_end is not null and expires_at <= v_payment.period_end + interval '1 minute';
        v_expired := found;
        if v_expired then
            perform public.billing_event(v_payment.user_id, 'subscription_expired', p_txn || ':refund_expired', p_txn,
                                         null, null, jsonb_build_object('reason', 'refund'));
        end if;
    end if;
    return jsonb_build_object('result', 'applied', 'status', v_status, 'expired', v_expired);
end;
$$;

-- Disputes (RF5, RF6). open: the payment is disputed and renewals stop (the caller cancels the mandate), Pro
-- stays. won: the payment goes back to paid. lost: treated as a full refund (RF3).
create function public.apply_dispute(p_txn text, p_state text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_payment public.payments;
begin
    if p_state not in ('open', 'won', 'lost') then
        raise exception 'invalid state %', p_state using errcode = '22023';
    end if;
    select * into v_payment from public.payments where txn_id = p_txn for update;
    if not found then
        return jsonb_build_object('error', 'not_found');
    end if;
    if v_payment.dispute_status is not distinct from p_state then
        return jsonb_build_object('result', 'unchanged', 'subscriptionId', v_payment.subscription_id);
    end if;

    if p_state = 'open' then
        update public.payments set status = 'disputed', dispute_status = 'open', updated_at = now()
        where id = v_payment.id;
        perform public.billing_event(v_payment.user_id, 'dispute_opened', p_txn || ':dispute_open', p_txn,
                                     v_payment.payu_ref, v_payment.amount_paise, null);
        return jsonb_build_object('result', 'applied', 'subscriptionId', v_payment.subscription_id);
    end if;

    update public.payments
    set dispute_status = p_state,
        status = case when p_state = 'won' then case when refunded_paise > 0 then 'partially_refunded' else 'success' end
                      else status end,
        updated_at = now()
    where id = v_payment.id;
    perform public.billing_event(v_payment.user_id, 'dispute_closed', p_txn || ':dispute_' || p_state, p_txn,
                                 v_payment.payu_ref, null, jsonb_build_object('outcome', p_state));
    if p_state = 'lost' then
        update public.payments set status = 'success' where id = v_payment.id;
        return public.apply_refund_result(p_txn, 'success', v_payment.amount_paise)
            || jsonb_build_object('subscriptionId', v_payment.subscription_id);
    end if;
    return jsonb_build_object('result', 'applied', 'subscriptionId', v_payment.subscription_id);
end;
$$;

-- Our transaction ID for a PayU payment reference, for refund and dispute webhooks that carry only that.
create function public.payment_by_ref(p_ref text)
returns text
language sql
stable
security definer
set search_path = ''
as $$
    select txn_id from public.payments where payu_ref = p_ref
$$;

-- Everything about one user (AD1), now with payments and the payment event timeline.
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
                'status', p.status,
                'amountPaise', p.amount_paise,
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

revoke execute on function public.admin_list_payments(uuid, text, text, integer, integer) from public, anon, authenticated;
revoke execute on function public.admin_billing_health(uuid) from public, anon, authenticated;
revoke execute on function public.admin_billing_action(uuid, text, uuid, text) from public, anon, authenticated;
revoke execute on function public.record_refund_request(text, text) from public, anon, authenticated;
revoke execute on function public.claim_pending_refunds(integer) from public, anon, authenticated;
revoke execute on function public.apply_refund_result(text, text, integer) from public, anon, authenticated;
revoke execute on function public.apply_dispute(text, text) from public, anon, authenticated;
revoke execute on function public.payment_by_ref(text) from public, anon, authenticated;

grant execute on function public.admin_list_payments(uuid, text, text, integer, integer) to service_role;
grant execute on function public.admin_billing_health(uuid) to service_role;
grant execute on function public.admin_billing_action(uuid, text, uuid, text) to service_role;
grant execute on function public.record_refund_request(text, text) to service_role;
grant execute on function public.claim_pending_refunds(integer) to service_role;
grant execute on function public.apply_refund_result(text, text, integer) to service_role;
grant execute on function public.apply_dispute(text, text) to service_role;
grant execute on function public.payment_by_ref(text) to service_role;
