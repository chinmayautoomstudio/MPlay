-- MP3 Studio v3.2 plans and gating (M2): the plan a user is on and the one-time 30-day trial.
-- Both functions run only as the service role (from the `entitlements` and `claim-trial` Edge Functions);
-- the app can't call them directly.

-- The caller's plan, trial and subscription, as the Edge Functions return it to the app.
-- Pro: a subscription that is active, or cancelled but still inside the paid period.
-- Trial: a trial that hasn't ended. Everyone else is Free.
create function public.compute_entitlements(p_user uuid)
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
    v_plan text;
begin
    select * into v_profile from public.profiles where id = p_user;
    if not found then
        return null;
    end if;

    select * into v_trial from public.trials where user_id = p_user;

    select * into v_sub from public.subscriptions
    where user_id = p_user
      and plan = 'pro'
      and (status = 'active' or (status = 'cancelled' and expires_at > now()))
      and (expires_at is null or expires_at > now())
    order by expires_at desc nulls first
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
            'cancelAtPeriodEnd', v_sub.cancel_at_period_end
        ) end,
        'serverTime', now()
    );
end;
$$;

-- Grants the 30-day trial once per user, per normalized email and per device (PRD TR4, TR5).
-- The hashes are made by the Edge Function with a server-side pepper; the email comes from the verified
-- token, never from the request. Returns {"result": "granted" | "existing" | "denied", "reason": ...}.
create function public.claim_trial(p_user uuid, p_email_hash text, p_device_hash text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_claim uuid;
begin
    -- Parallel claims for the same user wait for each other instead of one being denied by the other's claim.
    perform pg_advisory_xact_lock(hashtextextended('claim_trial:' || p_user::text, 0));

    if exists (select 1 from public.trials where user_id = p_user) then
        return jsonb_build_object('result', 'existing');
    end if;
    if not exists (select 1 from public.profiles where id = p_user and not disabled) then
        return jsonb_build_object('result', 'denied', 'reason', 'account');
    end if;

    insert into public.trial_claims (email_hash, device_hash, user_id)
    values (p_email_hash, p_device_hash, p_user)
    on conflict do nothing
    returning id into v_claim;

    if v_claim is null then
        return jsonb_build_object(
            'result', 'denied',
            'reason', case
                when exists (select 1 from public.trial_claims where email_hash = p_email_hash) then 'email'
                else 'device'
            end
        );
    end if;

    insert into public.trials (user_id, started_at, ends_at, used)
    values (p_user, now(), now() + interval '30 days', true);

    return jsonb_build_object('result', 'granted');
end;
$$;

revoke execute on function public.compute_entitlements(uuid) from public, anon, authenticated;
revoke execute on function public.claim_trial(uuid, text, text) from public, anon, authenticated;
grant execute on function public.compute_entitlements(uuid) to service_role;
grant execute on function public.claim_trial(uuid, text, text) to service_role;
