-- MP3 Studio v3.2 foundation (M1): profiles, roles and the tables later phases fill in.
-- Every table has Row Level Security. The app (role `authenticated`) can read only its own rows and can
-- update only profiles.display_name and profiles.avatar_url. Everything else is written by server functions
-- running as the service role, which bypasses RLS.

create type public.app_role as enum ('user', 'admin');
create type public.plan_type as enum ('free', 'pro');

-- Profiles ------------------------------------------------------------------------------------------------

create table public.profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    display_name text check (char_length(display_name) <= 80),
    email text,
    avatar_url text,
    role public.app_role not null default 'user',
    disabled boolean not null default false,
    created_at timestamptz not null default now()
);

alter table public.profiles enable row level security;

revoke all on public.profiles from anon, authenticated;
grant select on public.profiles to authenticated;
grant update (display_name, avatar_url) on public.profiles to authenticated;

create policy "Users read their own profile"
    on public.profiles for select to authenticated
    using (id = (select auth.uid()));

create policy "Users update their own profile"
    on public.profiles for update to authenticated
    using (id = (select auth.uid()))
    with check (id = (select auth.uid()));

-- A new auth user always gets a profile with role 'user', filled from the Google account.
create function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.profiles (id, email, display_name, avatar_url)
    values (
        new.id,
        new.email,
        left(coalesce(new.raw_user_meta_data ->> 'full_name', new.raw_user_meta_data ->> 'name'), 80),
        coalesce(new.raw_user_meta_data ->> 'avatar_url', new.raw_user_meta_data ->> 'picture')
    );
    return new;
end;
$$;

create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- True when the caller is an enabled Admin. Used by admin server functions in later phases.
create function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1 from public.profiles
        where id = (select auth.uid()) and role = 'admin' and not disabled
    );
$$;

revoke execute on function public.is_admin() from public, anon;
grant execute on function public.is_admin() to authenticated;
revoke execute on function public.handle_new_user() from public, anon, authenticated;

-- Subscriptions (M4) --------------------------------------------------------------------------------------

create table public.subscriptions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    plan public.plan_type not null default 'pro',
    status text not null check (status in ('pending', 'active', 'cancelled', 'expired', 'halted')),
    provider text not null check (provider in ('razorpay', 'play')),
    provider_ref text not null,
    started_at timestamptz,
    expires_at timestamptz,
    next_billing_at timestamptz,
    last_payment_at timestamptz,
    payment_status text,
    cancel_at_period_end boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (provider, provider_ref)
);

create index subscriptions_user_id_idx on public.subscriptions (user_id);

-- Trials (M2) ---------------------------------------------------------------------------------------------

create table public.trials (
    user_id uuid primary key references auth.users (id) on delete cascade,
    started_at timestamptz not null,
    ends_at timestamptz not null,
    used boolean not null default true,
    created_at timestamptz not null default now()
);

-- Kept after account deletion (user_id is cleared) so deleting and re-registering doesn't grant a new trial.
create table public.trial_claims (
    id uuid primary key default gen_random_uuid(),
    email_hash text not null unique,
    device_hash text unique,
    user_id uuid references auth.users (id) on delete set null,
    claimed_at timestamptz not null default now()
);

-- Payment events (M4) -------------------------------------------------------------------------------------

create table public.payment_events (
    id bigint generated always as identity primary key,
    user_id uuid references auth.users (id) on delete set null,
    provider text not null check (provider in ('razorpay', 'play')),
    provider_event_id text not null,
    event_type text not null,
    provider_ref text,
    amount_paise integer,
    currency text,
    payload jsonb,
    created_at timestamptz not null default now(),
    unique (provider, provider_event_id)
);

create index payment_events_user_id_idx on public.payment_events (user_id);

-- AI Vocal Separator usage (M3) ---------------------------------------------------------------------------

create table public.ai_usage (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    job_ref text not null,
    song_ref text,
    status text not null check (status in ('reserved', 'completed', 'released')),
    reserved_at timestamptz not null default now(),
    completed_at timestamptz,
    week_start date not null,
    unique (user_id, job_ref)
);

create index ai_usage_user_week_idx on public.ai_usage (user_id, week_start);

-- Admin audit log (M5) ------------------------------------------------------------------------------------

create table public.admin_audit_log (
    id bigint generated always as identity primary key,
    actor_id uuid references auth.users (id) on delete set null,
    action text not null,
    target_user_id uuid,
    details jsonb,
    created_at timestamptz not null default now()
);

-- Access --------------------------------------------------------------------------------------------------

alter table public.subscriptions enable row level security;
alter table public.trials enable row level security;
alter table public.trial_claims enable row level security;
alter table public.payment_events enable row level security;
alter table public.ai_usage enable row level security;
alter table public.admin_audit_log enable row level security;

revoke all on public.subscriptions, public.trials, public.trial_claims, public.payment_events,
    public.ai_usage, public.admin_audit_log from anon, authenticated;

-- Users may read their own plan, trial and usage rows; nothing else is reachable from the app.
grant select on public.subscriptions, public.trials, public.ai_usage to authenticated;

create policy "Users read their own subscriptions"
    on public.subscriptions for select to authenticated
    using (user_id = (select auth.uid()));

create policy "Users read their own trial"
    on public.trials for select to authenticated
    using (user_id = (select auth.uid()));

create policy "Users read their own AI usage"
    on public.ai_usage for select to authenticated
    using (user_id = (select auth.uid()));
