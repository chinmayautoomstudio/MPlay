-- MP3 Studio v3.2 admin (M5): the in-app Admin screens (PRD AD1-AD11), admin invites by email and manual Pro
-- grants while payments are not built. Every function takes the caller's user id (p_actor, from the verified
-- token in the `admin` Edge Function), refuses anyone who isn't an enabled Admin and runs only as the service
-- role. Every change is written to admin_audit_log (AD9). Refusals the app explains come back as
-- {"error": "..."}; a caller who isn't an Admin gets SQLSTATE 42501.

-- Emails that become Admin on their first sign-in. Only the service role reads or writes them.
create table public.admin_invites (
    email text primary key check (email = lower(btrim(email))),
    invited_by uuid references auth.users (id) on delete set null,
    created_at timestamptz not null default now()
);

alter table public.admin_invites enable row level security;
revoke all on public.admin_invites from anon, authenticated;

-- Pro granted by an Admin is a subscription with provider 'admin' and provider_ref 'admin:<user id>'.
alter table public.subscriptions drop constraint subscriptions_provider_check;
alter table public.subscriptions add constraint subscriptions_provider_check
    check (provider in ('razorpay', 'play', 'admin'));

-- New auth users still always get a profile; an invited email starts as Admin and the invite is used up.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_invite public.admin_invites;
begin
    if new.email is not null then
        delete from public.admin_invites where email = lower(btrim(new.email)) returning * into v_invite;
    end if;

    insert into public.profiles (id, email, display_name, avatar_url, role)
    values (
        new.id,
        new.email,
        left(coalesce(new.raw_user_meta_data ->> 'full_name', new.raw_user_meta_data ->> 'name'), 80),
        coalesce(new.raw_user_meta_data ->> 'avatar_url', new.raw_user_meta_data ->> 'picture'),
        case when v_invite.email is null then 'user' else 'admin' end::public.app_role
    );

    if v_invite.email is not null then
        insert into public.admin_audit_log (actor_id, action, target_user_id, details)
        values (v_invite.invited_by, 'invite_accepted', new.id, jsonb_build_object('email', v_invite.email));
    end if;
    return new;
end;
$$;

revoke execute on function public.handle_new_user() from public, anon, authenticated;

-- Helpers -------------------------------------------------------------------------------------------------

create function public.assert_admin(p_actor uuid)
returns void
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if p_actor is null or not exists (
        select 1 from public.profiles where id = p_actor and role = 'admin' and not disabled
    ) then
        raise exception 'admin only' using errcode = '42501';
    end if;
end;
$$;

-- The same plan rule as compute_entitlements, cheap enough to run per row in lists.
create function public.plan_of(p_user uuid)
returns text
language sql
stable
security definer
set search_path = ''
as $$
    select case
        when exists (
            select 1 from public.subscriptions s
            where s.user_id = p_user and s.plan = 'pro'
              and (s.status = 'active' or (s.status = 'cancelled' and s.expires_at > now()))
              and (s.expires_at is null or s.expires_at > now())
        ) then 'pro'
        when exists (select 1 from public.trials t where t.user_id = p_user and t.ends_at > now()) then 'trial'
        else 'free'
    end;
$$;

create function public.admin_log(p_actor uuid, p_action text, p_target uuid, p_details jsonb)
returns void
language sql
security definer
set search_path = ''
as $$
    insert into public.admin_audit_log (actor_id, action, target_user_id, details)
    values (p_actor, p_action, p_target, p_details);
$$;

-- Admin functions -----------------------------------------------------------------------------------------

-- Counts for the Admin home screen (AD4) and this week's separator activity.
create function public.admin_overview(p_actor uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_week date := public.usage_week_start(now());
    v_result jsonb;
begin
    perform public.assert_admin(p_actor);

    with plans as (select p.role, p.disabled, public.plan_of(p.id) as plan from public.profiles p)
    select jsonb_build_object(
        'users', count(*),
        'pro', count(*) filter (where plan = 'pro'),
        'trial', count(*) filter (where plan = 'trial'),
        'free', count(*) filter (where plan = 'free'),
        'disabled', count(*) filter (where disabled),
        'admins', count(*) filter (where role = 'admin')
    ) into v_result from plans;

    return v_result || (
        select jsonb_build_object(
            'weekStart', v_week,
            'completed', count(*) filter (where status = 'completed'),
            'reserved', count(*) filter (where status = 'reserved' and reserved_at > now() - interval '48 hours'),
            'denied', count(*) filter (where status = 'denied')
        )
        from public.ai_usage where week_start = v_week
    );
end;
$$;

-- Users matching p_query (email or name) and p_filter: all, pro, trial, free, disabled or admin (AD2).
create function public.admin_list_users(
    p_actor uuid, p_query text, p_filter text, p_limit integer, p_offset integer
)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_week date := public.usage_week_start(now());
    v_pattern text := '%' || replace(replace(replace(coalesce(btrim(p_query), ''), '\', '\\'), '%', '\%'), '_', '\_') || '%';
    v_result jsonb;
begin
    perform public.assert_admin(p_actor);
    if p_filter not in ('all', 'pro', 'trial', 'free', 'disabled', 'admin') then
        raise exception 'invalid filter %', p_filter using errcode = '22023';
    end if;

    with matched as (
        select p.*, public.plan_of(p.id) as plan
        from public.profiles p
        where p.email ilike v_pattern or p.display_name ilike v_pattern
    ), filtered as (
        select * from matched
        where case p_filter
            when 'all' then true
            when 'disabled' then disabled
            when 'admin' then role = 'admin'
            else plan = p_filter
        end
    ), page as (
        select * from filtered
        order by created_at desc, id
        limit least(greatest(p_limit, 1), 100) offset greatest(p_offset, 0)
    )
    select jsonb_build_object(
        'total', (select count(*) from filtered),
        'users', coalesce((
            select jsonb_agg(jsonb_build_object(
                'id', u.id,
                'email', u.email,
                'name', u.display_name,
                'role', u.role,
                'plan', u.plan,
                'disabled', u.disabled,
                'createdAt', u.created_at,
                'usedThisWeek', (
                    select count(*) from public.ai_usage a
                    where a.user_id = u.id and a.status = 'completed' and a.week_start = v_week
                )
            ) order by u.created_at desc, u.id)
            from page u
        ), '[]'::jsonb)
    ) into v_result;
    return v_result;
end;
$$;

-- Everything about one user (AD3, AD5, AD8). {"error": "not_found"} for an unknown id.
create function public.admin_user_detail(p_actor uuid, p_user uuid)
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
                'cancelAtPeriodEnd', s.cancel_at_period_end
            ) order by s.created_at desc)
            from public.subscriptions s where s.user_id = p_user
        ), '[]'::jsonb),
        'events', coalesce((
            select jsonb_agg(jsonb_build_object(
                'provider', e.provider,
                'type', e.event_type,
                'amountPaise', e.amount_paise,
                'currency', e.currency,
                'createdAt', e.created_at
            ) order by e.created_at desc)
            from (select * from public.payment_events where user_id = p_user order by created_at desc limit 50) e
        ), '[]'::jsonb)
    );
end;
$$;

-- Separator use per week for the last 8 weeks and this week's top 20 users (AD5).
create function public.admin_usage(p_actor uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_week date := public.usage_week_start(now());
begin
    perform public.assert_admin(p_actor);
    return jsonb_build_object(
        'weeks', coalesce((
            select jsonb_agg(jsonb_build_object(
                'weekStart', w.week,
                'completed', (select count(*) from public.ai_usage a where a.week_start = w.week and a.status = 'completed'),
                'released', (select count(*) from public.ai_usage a where a.week_start = w.week and a.status = 'released'),
                'denied', (select count(*) from public.ai_usage a where a.week_start = w.week and a.status = 'denied'),
                'users', (select count(distinct a.user_id) from public.ai_usage a
                          where a.week_start = w.week and a.status = 'completed')
            ) order by w.week desc)
            from (select (v_week - 7 * g)::date as week from generate_series(0, 7) g) w
        ), '[]'::jsonb),
        'top', coalesce((
            select jsonb_agg(jsonb_build_object('id', t.user_id, 'email', p.email, 'name', p.display_name,
                                                'completed', t.completed) order by t.completed desc, p.email)
            from (
                select user_id, count(*) as completed from public.ai_usage
                where week_start = v_week and status = 'completed'
                group by user_id order by count(*) desc limit 20
            ) t
            join public.profiles p on p.id = t.user_id
        ), '[]'::jsonb)
    );
end;
$$;

-- User or Admin (AD6). The last enabled Admin can't be demoted, not even by themselves.
create function public.admin_set_role(p_actor uuid, p_user uuid, p_role text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_profile public.profiles;
begin
    perform public.assert_admin(p_actor);
    if p_role not in ('user', 'admin') then
        raise exception 'invalid role %', p_role using errcode = '22023';
    end if;
    -- Role and disable changes wait for each other, so two Admins can't demote each other at once.
    perform pg_advisory_xact_lock(hashtextextended('admin:roles', 0));

    select * into v_profile from public.profiles where id = p_user for update;
    if not found then
        return jsonb_build_object('error', 'not_found');
    end if;
    if v_profile.role::text = p_role then
        return jsonb_build_object('role', p_role);
    end if;
    if p_role = 'user' and not v_profile.disabled and not exists (
        select 1 from public.profiles where role = 'admin' and not disabled and id <> p_user
    ) then
        return jsonb_build_object('error', 'last_admin');
    end if;

    update public.profiles set role = p_role::public.app_role where id = p_user;
    perform public.admin_log(p_actor, 'set_role', p_user,
                             jsonb_build_object('from', v_profile.role, 'to', p_role));
    return jsonb_build_object('role', p_role);
end;
$$;

-- Disable or enable an account (AD7). Admins can't disable themselves (AD10) or the last enabled Admin.
create function public.admin_set_disabled(p_actor uuid, p_user uuid, p_disabled boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_profile public.profiles;
begin
    perform public.assert_admin(p_actor);
    if p_disabled and p_user = p_actor then
        return jsonb_build_object('error', 'self');
    end if;
    perform pg_advisory_xact_lock(hashtextextended('admin:roles', 0));

    select * into v_profile from public.profiles where id = p_user for update;
    if not found then
        return jsonb_build_object('error', 'not_found');
    end if;
    if v_profile.disabled = p_disabled then
        return jsonb_build_object('disabled', p_disabled);
    end if;
    if p_disabled and v_profile.role = 'admin' and not exists (
        select 1 from public.profiles where role = 'admin' and not disabled and id <> p_user
    ) then
        return jsonb_build_object('error', 'last_admin');
    end if;

    update public.profiles set disabled = p_disabled where id = p_user;
    perform public.admin_log(p_actor, case when p_disabled then 'disable' else 'enable' end, p_user, null);
    return jsonb_build_object('disabled', p_disabled);
end;
$$;

-- Makes an email an Admin: at once when the account exists, otherwise as an invite for its first sign-in.
-- Returns {"result": "promoted" | "already_admin" | "invited"} or {"error": "invalid_email"}.
create function public.admin_add_admin(p_actor uuid, p_email text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_email text := lower(btrim(p_email));
    v_profile public.profiles;
begin
    perform public.assert_admin(p_actor);
    if v_email is null or v_email !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' or char_length(v_email) > 254 then
        return jsonb_build_object('error', 'invalid_email');
    end if;
    perform pg_advisory_xact_lock(hashtextextended('admin:roles', 0));

    select * into v_profile from public.profiles where lower(email) = v_email
    order by created_at limit 1 for update;
    if found then
        if v_profile.role = 'admin' then
            return jsonb_build_object('result', 'already_admin', 'userId', v_profile.id);
        end if;
        update public.profiles set role = 'admin' where id = v_profile.id;
        perform public.admin_log(p_actor, 'set_role', v_profile.id,
                                 jsonb_build_object('from', 'user', 'to', 'admin', 'email', v_email));
        return jsonb_build_object('result', 'promoted', 'userId', v_profile.id);
    end if;

    insert into public.admin_invites (email, invited_by) values (v_email, p_actor)
    on conflict (email) do nothing;
    if found then
        perform public.admin_log(p_actor, 'invite_admin', null, jsonb_build_object('email', v_email));
    end if;
    return jsonb_build_object('result', 'invited');
end;
$$;

-- Current Admins and pending invites.
create function public.admin_list_admins(p_actor uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    perform public.assert_admin(p_actor);
    return jsonb_build_object(
        'admins', coalesce((
            select jsonb_agg(jsonb_build_object('id', p.id, 'email', p.email, 'name', p.display_name,
                                                'disabled', p.disabled) order by p.email)
            from public.profiles p where p.role = 'admin'
        ), '[]'::jsonb),
        'invites', coalesce((
            select jsonb_agg(jsonb_build_object('email', i.email, 'invitedBy', p.email, 'createdAt', i.created_at)
                             order by i.created_at desc)
            from public.admin_invites i left join public.profiles p on p.id = i.invited_by
        ), '[]'::jsonb)
    );
end;
$$;

create function public.admin_revoke_invite(p_actor uuid, p_email text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_email text := lower(btrim(p_email));
begin
    perform public.assert_admin(p_actor);
    delete from public.admin_invites where email = v_email;
    if found then
        perform public.admin_log(p_actor, 'revoke_invite', null, jsonb_build_object('email', v_email));
    end if;
    return jsonb_build_object('result', 'ok');
end;
$$;

-- Pro until p_until, granted by an Admin while payments are not built. compute_entitlements treats the
-- 'admin' subscription like any other active one. {"error": "invalid_date"} unless p_until is in the next 5 years.
create function public.admin_grant_pro(p_actor uuid, p_user uuid, p_until timestamptz)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
    perform public.assert_admin(p_actor);
    if p_until is null or p_until <= now() or p_until > now() + interval '5 years' then
        return jsonb_build_object('error', 'invalid_date');
    end if;
    if not exists (select 1 from public.profiles where id = p_user) then
        return jsonb_build_object('error', 'not_found');
    end if;

    insert into public.subscriptions (user_id, plan, status, provider, provider_ref, started_at, expires_at,
                                      payment_status)
    values (p_user, 'pro', 'active', 'admin', 'admin:' || p_user::text, now(), p_until, 'granted')
    on conflict (provider, provider_ref) do update
        set status = 'active',
            started_at = case when public.subscriptions.status = 'active' then public.subscriptions.started_at
                              else now() end,
            expires_at = excluded.expires_at,
            cancel_at_period_end = false,
            updated_at = now();

    perform public.admin_log(p_actor, 'grant_pro', p_user, jsonb_build_object('until', p_until));
    return jsonb_build_object('result', 'ok');
end;
$$;

create function public.admin_revoke_pro(p_actor uuid, p_user uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
    perform public.assert_admin(p_actor);
    update public.subscriptions
    set status = 'expired', expires_at = now(), updated_at = now()
    where provider = 'admin' and provider_ref = 'admin:' || p_user::text and status = 'active';
    if found then
        perform public.admin_log(p_actor, 'revoke_pro', p_user, null);
    end if;
    return jsonb_build_object('result', 'ok');
end;
$$;

-- Audit log, newest first; p_before is the id of the last entry already shown.
create function public.admin_audit(p_actor uuid, p_limit integer, p_before bigint)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    perform public.assert_admin(p_actor);
    return jsonb_build_object('entries', coalesce((
        select jsonb_agg(jsonb_build_object(
            'id', l.id,
            'action', l.action,
            'actorEmail', a.email,
            'targetId', l.target_user_id,
            'targetEmail', t.email,
            'details', l.details,
            'createdAt', l.created_at
        ) order by l.id desc)
        from (
            select * from public.admin_audit_log
            where p_before is null or id < p_before
            order by id desc
            limit least(greatest(p_limit, 1), 100)
        ) l
        left join public.profiles a on a.id = l.actor_id
        left join public.profiles t on t.id = l.target_user_id
    ), '[]'::jsonb));
end;
$$;

-- Access --------------------------------------------------------------------------------------------------

revoke execute on function public.assert_admin(uuid) from public, anon, authenticated;
revoke execute on function public.plan_of(uuid) from public, anon, authenticated;
revoke execute on function public.admin_log(uuid, text, uuid, jsonb) from public, anon, authenticated;
revoke execute on function public.admin_overview(uuid) from public, anon, authenticated;
revoke execute on function public.admin_list_users(uuid, text, text, integer, integer) from public, anon, authenticated;
revoke execute on function public.admin_user_detail(uuid, uuid) from public, anon, authenticated;
revoke execute on function public.admin_usage(uuid) from public, anon, authenticated;
revoke execute on function public.admin_set_role(uuid, uuid, text) from public, anon, authenticated;
revoke execute on function public.admin_set_disabled(uuid, uuid, boolean) from public, anon, authenticated;
revoke execute on function public.admin_add_admin(uuid, text) from public, anon, authenticated;
revoke execute on function public.admin_list_admins(uuid) from public, anon, authenticated;
revoke execute on function public.admin_revoke_invite(uuid, text) from public, anon, authenticated;
revoke execute on function public.admin_grant_pro(uuid, uuid, timestamptz) from public, anon, authenticated;
revoke execute on function public.admin_revoke_pro(uuid, uuid) from public, anon, authenticated;
revoke execute on function public.admin_audit(uuid, integer, bigint) from public, anon, authenticated;

grant execute on function public.admin_overview(uuid) to service_role;
grant execute on function public.admin_list_users(uuid, text, text, integer, integer) to service_role;
grant execute on function public.admin_user_detail(uuid, uuid) to service_role;
grant execute on function public.admin_usage(uuid) to service_role;
grant execute on function public.admin_set_role(uuid, uuid, text) to service_role;
grant execute on function public.admin_set_disabled(uuid, uuid, boolean) to service_role;
grant execute on function public.admin_add_admin(uuid, text) to service_role;
grant execute on function public.admin_list_admins(uuid) to service_role;
grant execute on function public.admin_revoke_invite(uuid, text) to service_role;
grant execute on function public.admin_grant_pro(uuid, uuid, timestamptz) to service_role;
grant execute on function public.admin_revoke_pro(uuid, uuid) to service_role;
grant execute on function public.admin_audit(uuid, integer, bigint) to service_role;
