-- Activity feed behind the bell on the Admin dashboard: new sign-ups, subscriptions that became active
-- (paid or granted by an Admin) and deleted accounts, newest first. Read only; nothing is stored.

create function public.admin_activity(p_actor uuid, p_limit integer)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    perform public.assert_admin(p_actor);
    return jsonb_build_object('events', coalesce((
        select jsonb_agg(to_jsonb(e) order by e.at desc)
        from (
            select * from (
                select 'signup' as type, p.created_at as at, p.id as "userId", p.email, p.display_name as name,
                       null::text as provider, null::text as plan
                from public.profiles p
                union all
                select 'subscribed', coalesce(s.started_at, s.created_at), s.user_id, p.email, p.display_name,
                       s.provider, s.plan::text
                from public.subscriptions s
                left join public.profiles p on p.id = s.user_id
                where s.status = 'active'
                union all
                select 'deleted', l.created_at, null, null, null, null, l.details ->> 'plan'
                from public.admin_audit_log l
                where l.action = 'account_deleted'
            ) all_events
            order by at desc
            limit least(greatest(coalesce(p_limit, 50), 1), 100)
        ) e
    ), '[]'::jsonb));
end;
$$;

revoke execute on function public.admin_activity(uuid, integer) from public, anon, authenticated;
grant execute on function public.admin_activity(uuid, integer) to service_role;
