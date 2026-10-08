-- MP3 Studio v3.2 account deletion (M6, PRD AU9, PR4): the signed-in user deletes their own account from the app.
-- Deleting the auth.users row removes everything that belongs to the account through the foreign keys:
-- profiles, subscriptions, trials and ai_usage cascade. trial_claims, payment_events, admin_audit_log.actor_id and
-- admin_invites.invited_by are set to null, so trial hashes survive (deleting and signing up again doesn't give a
-- new trial) and payment records are kept. Runs only as the service role, from the `delete-account` Edge Function.

-- Returns {"result": "deleted"} or {"error": "not_found" | "last_admin" | "active_subscription"}.
create function public.delete_account(p_user uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_profile public.profiles;
begin
    -- Waits for Admin role and disable changes, so the last Admin can't be removed by a delete racing a demotion.
    perform pg_advisory_xact_lock(hashtextextended('admin:roles', 0));

    select * into v_profile from public.profiles where id = p_user for update;
    if not found then
        return jsonb_build_object('error', 'not_found');
    end if;
    if v_profile.role = 'admin' and not v_profile.disabled and not exists (
        select 1 from public.profiles where role = 'admin' and not disabled and id <> p_user
    ) then
        return jsonb_build_object('error', 'last_admin');
    end if;
    -- A paid subscription that still renews has to be cancelled with the provider first (M4).
    if exists (
        select 1 from public.subscriptions
        where user_id = p_user and provider in ('razorpay', 'play') and status = 'active'
          and not cancel_at_period_end
    ) then
        return jsonb_build_object('error', 'active_subscription');
    end if;

    -- No email in the log: the account's personal data goes with it. actor_id becomes null with the user.
    perform public.admin_log(p_user, 'account_deleted', p_user,
                             jsonb_build_object('role', v_profile.role, 'plan', public.plan_of(p_user)));
    delete from auth.users where id = p_user;
    return jsonb_build_object('result', 'deleted');
end;
$$;

revoke execute on function public.delete_account(uuid) from public, anon, authenticated;
grant execute on function public.delete_account(uuid) to service_role;
