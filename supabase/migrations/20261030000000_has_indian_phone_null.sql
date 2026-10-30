-- When profiles.phone is null, `phone ~ regex` is SQL NULL. jsonb_build_object keeps that
-- as JSON null, and the Android client rejected it (boolean expected). Coalesce to false.

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
        'hasIndianPhone', coalesce(v_profile.phone ~ '^[6-9][0-9]{9}$', false),
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
