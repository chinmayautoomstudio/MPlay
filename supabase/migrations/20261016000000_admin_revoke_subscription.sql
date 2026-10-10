-- Admin "Revoke subscription": ends a user's PayU Pro now, unlike cancel_subscription, which keeps Pro until the
-- paid period ends. A live mandate is cancelled with PayU by the admin Edge Function; if PayU doesn't confirm,
-- mandate_cancel_requested_at stays set and the renewals job retries it. Nothing is refunded.

-- Checks and logs an Admin billing action before the Edge Function calls PayU (AD3, AD4, RF2).
-- p_action: reverify (p_txn), refund (p_txn), cancel_subscription or revoke_subscription (p_user). Returns what the
-- call needs, or {"error": "not_found" | "not_refundable" | "not_subscribed"}.
create or replace function public.admin_billing_action(p_actor uuid, p_action text, p_user uuid, p_txn text)
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
    if p_action not in ('reverify', 'refund', 'cancel_subscription', 'revoke_subscription') then
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

    if p_action = 'revoke_subscription' then
        select * into v_sub from public.subscriptions
        where user_id = p_user and provider = 'payu' and status in ('active', 'past_due', 'cancelled')
          and expires_at > now()
        order by expires_at desc
        limit 1;
        if not found then
            return jsonb_build_object('error', 'not_subscribed');
        end if;
        perform public.admin_log(p_actor, 'revoke_subscription', p_user, jsonb_build_object('subscription', v_sub.id));
        return jsonb_build_object('userId', p_user, 'subscriptionId', v_sub.id);
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

-- Ends a PayU subscription now: Pro stops, renewals stop and a live mandate is marked for cancellation. Returns
-- {"result": "revoked"}, plus mandateRef and method when the caller must cancel a live mandate with PayU, or
-- {"error": "not_subscribed"} when the subscription no longer gives Pro.
create function public.revoke_subscription(p_sub uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_sub public.subscriptions;
    v_request jsonb;
begin
    select * into v_sub from public.subscriptions where id = p_sub and provider = 'payu';
    if not found then
        return jsonb_build_object('error', 'not_subscribed');
    end if;
    perform pg_advisory_xact_lock(hashtextextended('billing:' || v_sub.user_id::text, 0));
    select * into v_sub from public.subscriptions where id = p_sub for update;
    if v_sub.status not in ('active', 'past_due', 'cancelled') or v_sub.expires_at <= now() then
        return jsonb_build_object('error', 'not_subscribed');
    end if;

    v_request := public.request_mandate_cancel(p_sub, true);
    update public.subscriptions
    set status = 'expired', expires_at = now(), next_billing_at = null, grace_end = null,
        cancel_at_period_end = true, pre_debit_for = null, pre_debit_sent_at = null, updated_at = now()
    where id = p_sub
    returning * into v_sub;
    perform public.billing_event(v_sub.user_id, 'subscription_revoked', 'revoked:' || v_sub.id, null, null, null,
                                 jsonb_build_object('endedAt', v_sub.expires_at));

    if v_request ? 'mandateRef' then
        return jsonb_build_object('result', 'revoked', 'mandateRef', v_request ->> 'mandateRef',
                                  'method', v_request ->> 'method');
    end if;
    return jsonb_build_object('result', 'revoked');
end;
$$;

revoke execute on function public.revoke_subscription(uuid) from public, anon, authenticated;
grant execute on function public.revoke_subscription(uuid) to service_role;
