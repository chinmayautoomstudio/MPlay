-- At most two logged-in devices per account. The app registers the phone after Google sign-in;
-- a third device must replace one of the two. Removing a device also drops its GoTrue session
-- so that phone is signed out on the next refresh.

create table public.user_devices (
    user_id uuid not null references auth.users (id) on delete cascade,
    device_id text not null check (device_id ~ '^[0-9a-f]{64}$'),
    device_name text not null default 'Android device'
        check (char_length(device_name) between 1 and 80),
    session_id uuid,
    signed_in_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now(),
    primary key (user_id, device_id)
);

create index user_devices_user_last_seen on public.user_devices (user_id, last_seen_at desc);

alter table public.user_devices enable row level security;

revoke all on public.user_devices from anon, authenticated;
grant select on public.user_devices to authenticated;

create policy "Users read their own devices"
    on public.user_devices for select to authenticated
    using (user_id = (select auth.uid()));

-- Helpers -------------------------------------------------------------------------------------------------

create function public.revoke_device_session(p_user uuid, p_session uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if p_session is null then
        return;
    end if;
    delete from auth.sessions where id = p_session and user_id = p_user;
end;
$$;

create function public.device_list(p_user uuid, p_current_device text default null)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
    select coalesce(jsonb_agg(jsonb_build_object(
        'deviceId', d.device_id,
        'deviceName', d.device_name,
        'signedInAt', d.signed_in_at,
        'lastSeenAt', d.last_seen_at,
        'current', p_current_device is not null and d.device_id = p_current_device
    ) order by d.last_seen_at desc, d.signed_in_at desc), '[]'::jsonb)
    from public.user_devices d
    where d.user_id = p_user;
$$;

create function public.register_device(
    p_user uuid,
    p_device_id text,
    p_device_name text,
    p_session_id uuid default null,
    p_replace_device_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_profile public.profiles;
    v_name text;
    v_old public.user_devices;
    v_count integer;
begin
    if p_device_id is null or p_device_id !~ '^[0-9a-f]{64}$' then
        raise exception 'invalid device id' using errcode = '22023';
    end if;
    if p_replace_device_id is not null and p_replace_device_id !~ '^[0-9a-f]{64}$' then
        raise exception 'invalid device id' using errcode = '22023';
    end if;
    v_name := coalesce(nullif(left(btrim(coalesce(p_device_name, '')), 80), ''), 'Android device');

    perform pg_advisory_xact_lock(hashtextextended('devices:' || p_user::text, 0));

    select * into v_profile from public.profiles where id = p_user for update;
    if not found then
        return jsonb_build_object('error', 'not_found');
    end if;
    if v_profile.disabled then
        return jsonb_build_object('error', 'account_disabled');
    end if;

    if p_replace_device_id is not null and p_replace_device_id is distinct from p_device_id then
        select * into v_old from public.user_devices
        where user_id = p_user and device_id = p_replace_device_id;
        if found then
            delete from public.user_devices
            where user_id = p_user and device_id = p_replace_device_id;
            perform public.revoke_device_session(p_user, v_old.session_id);
        end if;
    end if;

    if exists (
        select 1 from public.user_devices where user_id = p_user and device_id = p_device_id
    ) then
        update public.user_devices
        set device_name = v_name,
            session_id = coalesce(p_session_id, session_id),
            last_seen_at = now()
        where user_id = p_user and device_id = p_device_id;
        return jsonb_build_object('result', 'registered', 'devices', public.device_list(p_user, p_device_id));
    end if;

    select count(*) into v_count from public.user_devices where user_id = p_user;
    if v_count >= 2 then
        return jsonb_build_object(
            'result', 'limit_reached',
            'maxDevices', 2,
            'devices', public.device_list(p_user, p_device_id)
        );
    end if;

    insert into public.user_devices (user_id, device_id, device_name, session_id)
    values (p_user, p_device_id, v_name, p_session_id);
    return jsonb_build_object('result', 'registered', 'devices', public.device_list(p_user, p_device_id));
end;
$$;

create function public.verify_device(
    p_user uuid,
    p_device_id text,
    p_device_name text default null,
    p_session_id uuid default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_profile public.profiles;
    v_name text;
    v_count integer;
begin
    if p_device_id is null or p_device_id !~ '^[0-9a-f]{64}$' then
        raise exception 'invalid device id' using errcode = '22023';
    end if;
    v_name := coalesce(nullif(left(btrim(coalesce(p_device_name, '')), 80), ''), 'Android device');

    select * into v_profile from public.profiles where id = p_user;
    if not found then
        return jsonb_build_object('status', 'revoked');
    end if;
    if v_profile.disabled then
        return jsonb_build_object('status', 'disabled');
    end if;

    if exists (
        select 1 from public.user_devices where user_id = p_user and device_id = p_device_id
    ) then
        update public.user_devices
        set last_seen_at = now(),
            device_name = case when p_device_name is null then device_name else v_name end,
            session_id = coalesce(p_session_id, session_id)
        where user_id = p_user and device_id = p_device_id;
        return jsonb_build_object('status', 'ok', 'devices', public.device_list(p_user, p_device_id));
    end if;

    select count(*) into v_count from public.user_devices where user_id = p_user;
    if v_count = 0 then
        insert into public.user_devices (user_id, device_id, device_name, session_id)
        values (p_user, p_device_id, v_name, p_session_id);
        return jsonb_build_object('status', 'ok', 'devices', public.device_list(p_user, p_device_id));
    end if;

    return jsonb_build_object('status', 'revoked');
end;
$$;

create function public.remove_device(p_user uuid, p_device_id text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_old public.user_devices;
begin
    if p_device_id is null or p_device_id !~ '^[0-9a-f]{64}$' then
        raise exception 'invalid device id' using errcode = '22023';
    end if;
    perform pg_advisory_xact_lock(hashtextextended('devices:' || p_user::text, 0));

    select * into v_old from public.user_devices
    where user_id = p_user and device_id = p_device_id;
    if found then
        delete from public.user_devices where user_id = p_user and device_id = p_device_id;
        perform public.revoke_device_session(p_user, v_old.session_id);
    end if;
    return jsonb_build_object('result', 'removed', 'devices', public.device_list(p_user, null));
end;
$$;

create function public.admin_remove_device(p_actor uuid, p_user uuid, p_device_id text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_result jsonb;
begin
    perform public.assert_admin(p_actor);
    if not exists (select 1 from public.profiles where id = p_user) then
        return jsonb_build_object('error', 'not_found');
    end if;
    v_result := public.remove_device(p_user, p_device_id);
    perform public.admin_log(p_actor, 'remove_device', p_user, jsonb_build_object('deviceId', p_device_id));
    return v_result;
end;
$$;

-- User page: keep the live web-admin fields and add the active devices.
create or replace function public.admin_user_detail(p_actor uuid, p_user uuid)
returns jsonb
language plpgsql
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
            'phone', v_profile.phone,
            'role', v_profile.role,
            'disabled', v_profile.disabled,
            'createdAt', v_profile.created_at
        ),
        'entitlements', public.compute_entitlements(p_user),
        'usage', public.usage_summary(p_user),
        'devices', public.device_list(p_user, null),
        'trialClaim', (
            select jsonb_build_object('claimedAt', c.claimed_at, 'result', 'claimed')
            from public.trial_claims c
            where c.user_id = p_user
            order by c.claimed_at desc
            limit 1
        ),
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
                'id', s.id,
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
                'mandateRef', s.mandate_ref,
                'mandateStart', s.mandate_start,
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
                'mandateRef', p.mandate_ref,
                'method', p.method,
                'failureReason', p.failure_reason,
                'linkExpiresAt', p.link_expires_at,
                'resolveUntil', p.resolve_until,
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

revoke execute on function public.revoke_device_session(uuid, uuid) from public, anon, authenticated;
revoke execute on function public.device_list(uuid, text) from public, anon, authenticated;
revoke execute on function public.register_device(uuid, text, text, uuid, text) from public, anon, authenticated;
revoke execute on function public.verify_device(uuid, text, text, uuid) from public, anon, authenticated;
revoke execute on function public.remove_device(uuid, text) from public, anon, authenticated;
revoke execute on function public.admin_remove_device(uuid, uuid, text) from public, anon, authenticated;

grant execute on function public.revoke_device_session(uuid, uuid) to service_role;
grant execute on function public.device_list(uuid, text) to service_role;
grant execute on function public.register_device(uuid, text, text, uuid, text) to service_role;
grant execute on function public.verify_device(uuid, text, text, uuid) to service_role;
grant execute on function public.remove_device(uuid, text) to service_role;
grant execute on function public.admin_remove_device(uuid, uuid, text) to service_role;
