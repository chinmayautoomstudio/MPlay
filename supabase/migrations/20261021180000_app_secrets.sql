-- App secrets that must survive a Coolify .env rewrite. The Edge Functions still prefer
-- TRIAL_HASH_PEPPER / BILLING_JOBS_SECRET from the environment; if those are missing they
-- read the same values from Vault through this function (service_role only).

create function public.app_secret(p_name text)
returns text
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if p_name not in ('trial_hash_pepper', 'billing_jobs_secret') then
        return null;
    end if;
    return (select decrypted_secret from vault.decrypted_secrets where name = p_name);
end;
$$;

revoke execute on function public.app_secret(text) from public, anon, authenticated;
grant execute on function public.app_secret(text) to service_role;
