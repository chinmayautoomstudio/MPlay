-- MP3 Studio v3.2 usage limit (M3): Free users may run the AI Vocal Separator on 10 songs per week.
-- The app reserves a use before it queues a song and reports the outcome when the job ends. A use counts only
-- when the job completes (PRD US3). Weeks start on Monday 00:00 India Standard Time, so nothing has to run at
-- the reset (US8). All functions run only as the service role, from the Edge Functions.

-- Refused requests are recorded too, for admin reporting (US9).
alter table public.ai_usage drop constraint ai_usage_status_check;
alter table public.ai_usage add constraint ai_usage_status_check
    check (status in ('reserved', 'completed', 'released', 'denied'));

-- The Monday (India Standard Time) of the week that contains p_ts.
create function public.usage_week_start(p_ts timestamptz)
returns date
language sql
immutable
set search_path = ''
as $$
    select date_trunc('week', p_ts at time zone 'Asia/Kolkata')::date;
$$;

-- This week's separator usage. A reservation older than 48 hours stops holding a use, so a job that never
-- reports back (app uninstalled, data cleared) can't block the quota for long.
create function public.usage_summary(p_user uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_limit constant integer := 10;
    v_week date := public.usage_week_start(now());
    v_used integer;
    v_reserved integer;
    v_unlimited boolean;
begin
    select count(*) into v_used from public.ai_usage
    where user_id = p_user and status = 'completed' and week_start = v_week;

    select count(*) into v_reserved from public.ai_usage
    where user_id = p_user and status = 'reserved' and reserved_at > now() - interval '48 hours';

    v_unlimited := coalesce(public.compute_entitlements(p_user) ->> 'plan', 'free') in ('trial', 'pro');

    return jsonb_build_object(
        'limit', v_limit,
        'used', v_used,
        'reserved', v_reserved,
        'remaining', greatest(v_limit - v_used - v_reserved, 0),
        'weekStart', v_week,
        'resetsAt', (v_week + 7)::timestamp at time zone 'Asia/Kolkata',
        'unlimited', v_unlimited
    );
end;
$$;

-- Reserves one use per job (p_jobs: [{"jobRef": "...", "songRef": "..."}], in the order the user picked them).
-- Trial and Pro get every job; Free gets as many as remain and the rest are recorded as denied.
-- Asking again for a known jobRef returns the earlier answer. Returns {"granted": [...], "denied": [...],
-- "usage": {...}}, or {"error": "account"} for a missing or disabled profile.
create function public.reserve_separation(p_user uuid, p_jobs jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_week date := public.usage_week_start(now());
    v_usage jsonb;
    v_unlimited boolean;
    v_remaining integer;
    v_job jsonb;
    v_ref text;
    v_status text;
    v_granted jsonb := '[]'::jsonb;
    v_denied jsonb := '[]'::jsonb;
begin
    -- Parallel requests from the same user wait for each other, so together they can't pass the limit (US5).
    perform pg_advisory_xact_lock(hashtextextended('usage:' || p_user::text, 0));

    if not exists (select 1 from public.profiles where id = p_user and not disabled) then
        return jsonb_build_object('error', 'account');
    end if;

    v_usage := public.usage_summary(p_user);
    v_unlimited := (v_usage ->> 'unlimited')::boolean;
    v_remaining := (v_usage ->> 'remaining')::integer;

    for v_job in select value from jsonb_array_elements(p_jobs) loop
        v_ref := v_job ->> 'jobRef';
        continue when v_ref is null;

        select status into v_status from public.ai_usage where user_id = p_user and job_ref = v_ref;
        if found then
            if v_status in ('reserved', 'completed') then
                v_granted := v_granted || to_jsonb(v_ref);
            else
                v_denied := v_denied || to_jsonb(v_ref);
            end if;
            continue;
        end if;

        if v_unlimited or v_remaining > 0 then
            insert into public.ai_usage (user_id, job_ref, song_ref, status, week_start)
            values (p_user, v_ref, left(v_job ->> 'songRef', 200), 'reserved', v_week);
            v_granted := v_granted || to_jsonb(v_ref);
            if not v_unlimited then
                v_remaining := v_remaining - 1;
            end if;
        else
            insert into public.ai_usage (user_id, job_ref, song_ref, status, week_start)
            values (p_user, v_ref, left(v_job ->> 'songRef', 200), 'denied', v_week);
            v_denied := v_denied || to_jsonb(v_ref);
        end if;
    end loop;

    return jsonb_build_object('granted', v_granted, 'denied', v_denied, 'usage', public.usage_summary(p_user));
end;
$$;

-- Records how a reserved job ended: 'completed' counts as a use in the week it finished, 'released'
-- (cancelled or failed) gives the use back. A job with no reservation (Trial or Pro queued offline) gets a
-- row with its outcome. Reporting the same outcome again changes nothing. Returns {"usage": {...}}.
create function public.finish_separation(p_user uuid, p_job_ref text, p_outcome text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
    if p_outcome not in ('completed', 'released') then
        raise exception 'invalid outcome %', p_outcome using errcode = '22023';
    end if;

    perform pg_advisory_xact_lock(hashtextextended('usage:' || p_user::text, 0));

    update public.ai_usage
    set status = p_outcome,
        completed_at = case when p_outcome = 'completed' then now() end,
        week_start = case when p_outcome = 'completed' then public.usage_week_start(now()) else week_start end
    where user_id = p_user and job_ref = p_job_ref and status = 'reserved';

    if not found and not exists (
        select 1 from public.ai_usage where user_id = p_user and job_ref = p_job_ref
    ) then
        insert into public.ai_usage (user_id, job_ref, status, completed_at, week_start)
        values (
            p_user, p_job_ref, p_outcome,
            case when p_outcome = 'completed' then now() end,
            public.usage_week_start(now())
        );
    end if;

    return jsonb_build_object('usage', public.usage_summary(p_user));
end;
$$;

revoke execute on function public.usage_summary(uuid) from public, anon, authenticated;
revoke execute on function public.reserve_separation(uuid, jsonb) from public, anon, authenticated;
revoke execute on function public.finish_separation(uuid, text, text) from public, anon, authenticated;
grant execute on function public.usage_summary(uuid) to service_role;
grant execute on function public.reserve_separation(uuid, jsonb) to service_role;
grant execute on function public.finish_separation(uuid, text, text) to service_role;
