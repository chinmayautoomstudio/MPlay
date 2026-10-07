# Backend (Supabase)

> Last updated: 2026-10-07

MP3 Studio uses a self-hosted Supabase project for accounts and, in later milestones, plans, trials, AI usage and payments. Music, playlists, stems and recordings never leave the phone. The app talks to Supabase Auth (GoTrue) and PostgREST with the public anon key; everything privileged runs server-side as the service role.

## Project

| Item | Value |
|---|---|
| URL | `https://securedbmp3.autoomstudio.com` (self-hosted, Postgres 15) |
| Repo folder | [`supabase/`](../supabase) |
| Config | [`supabase/config.toml`](../supabase/config.toml) (`project_id = "mp3-studio"`) |
| Migrations | [`supabase/migrations/`](../supabase/migrations) |
| RLS tests | [`supabase/tests/rls_test.sql`](../supabase/tests/rls_test.sql) (pgTAP, 17 checks) |
| First Admin | [`supabase/seed/first_admin.sql`](../supabase/seed/first_admin.sql) (run by hand, not seeded) |

The app reads the URL and anon key from `local.properties` (`supabase.url`, `supabase.key`) together with `google.webClientId`; see [accounts](features/accounts.md#build-configuration).

## Auth settings

Sign-in is Google only (PRD AU2). `config.toml` turns off email and phone sign-up and enables `[auth.external.google]` with `skip_nonce_check = false`, so the ID token's nonce must match the one the app sends.

On the self-hosted server, `config.toml` isn't applied automatically. Set these in the GoTrue environment as well:

- `GOTRUE_EXTERNAL_GOOGLE_ENABLED=true`, `GOTRUE_EXTERNAL_GOOGLE_CLIENT_ID=<Web client ID>` (the same ID as `google.webClientId`), `GOTRUE_EXTERNAL_GOOGLE_SECRET=<Web client secret>`.
- `GOTRUE_EXTERNAL_EMAIL_ENABLED=false` and `GOTRUE_EXTERNAL_PHONE_ENABLED=false`. As of 2026-10-07 `/auth/v1/settings` still reports email and phone as enabled.

To disable an account, an Admin sets `profiles.disabled = true` (the app signs out on the next foreground check). Banning the user in GoTrue also works: the refresh fails with `user_banned`.

## Tables

All in schema `public`, all with Row Level Security. `anon` has no access to any of them.

| Table | Purpose | App (`authenticated`) access | Filled by |
|---|---|---|---|
| `profiles` | One row per user: `display_name` (max 80), `email`, `avatar_url`, `role` (`app_role`: `user`/`admin`), `disabled`, `created_at`. | Select own row; update only `display_name` and `avatar_url` (column grants). | Trigger `on_auth_user_created` |
| `subscriptions` | Pro subscriptions (`provider` `razorpay`/`play`, status, billing dates). | Select own rows. | Server functions (M4) |
| `trials` | 30-day trial per user. | Select own row. | Server functions (M2) |
| `trial_claims` | Email and device hashes that already used a trial; `user_id` is cleared on account deletion, so the claim survives. | None. | Server functions (M2) |
| `payment_events` | Raw provider webhooks, unique per `(provider, provider_event_id)`. | None. | Server functions (M4) |
| `ai_usage` | AI Vocal Separator reservations per `job_ref` and `week_start`. | Select own rows. | Server functions (M3) |
| `admin_audit_log` | Admin actions. | None. | Server functions (M5) |

Functions:

- `handle_new_user()` (security definer, `search_path = ''`): inserts the profile with `role = 'user'`, the email, `full_name`/`name` (cut to 80) and `avatar_url`/`picture` from the Google metadata. Not executable by API roles.
- `is_admin()` (security definer): true when the caller's profile is an enabled Admin. Executable by `authenticated` only.

Supabase's security advisor reports "RLS enabled, no policy" for `trial_claims`, `payment_events` and `admin_audit_log`. That is intended: only the service role uses them.

## Migrations

| Version | File | Change |
|---|---|---|
| `20261007000000` | `20261007000000_foundation.sql` | Enums, all tables above, RLS, grants, profile trigger, `is_admin()`. |

The live database was migrated on 2026-10-07 through the Supabase MCP `apply_migration` tool with the name `foundation`, so its recorded version is the apply time rather than `20261007000000`. Before running `supabase db push` against it, run `supabase migration repair --status applied 20261007000000` so the CLI doesn't apply the file twice.

## Testing RLS

`supabase/tests/rls_test.sql` checks the trigger, that a user sees only their own profile, can rename themselves but not change `role` or `disabled`, that `is_admin()` is false for normal users, that writes to plan, trial and usage tables fail, that the service-only tables are unreadable, and that `anon` sees nothing. Run it with `supabase test db` against a local stack. The same checks were run once against the live database inside a transaction that was rolled back.

## First Admin

Sign in once with the Google account, then run `supabase/seed/first_admin.sql` (with that email) in the SQL editor. Later Admins are promoted from the in-app Admin screens (M5).

## Change history

| Date | Commit | Change |
|---|---|---|
| 2026-10-07 | - | Project folder, foundation migration, RLS tests, first-Admin snippet; live database migrated. |
