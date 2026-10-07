-- Promotes the first Admin (PRD AD11). Admins are never created through the app.
--
-- 1. Sign in to MP3 Studio once with the owner's Google account, so the auth user and profile exist.
-- 2. Run this in the Supabase SQL editor (it runs as postgres, which bypasses RLS), with the email filled in.
-- 3. Sign out and back in on the phone; later Admins are promoted from the in-app Admin screens (M5).

update public.profiles
set role = 'admin'
where email = 'owner@example.com'
returning id, email, role;
